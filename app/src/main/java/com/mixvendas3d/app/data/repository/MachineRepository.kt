package com.mixvendas3d.app.data.repository

import com.mixvendas3d.app.data.local.dao.MachineDao
import com.mixvendas3d.app.data.local.entity.MachineBrandRateEntity
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.local.entity.MachineInstallmentRateEntity
import com.mixvendas3d.app.data.local.entity.MachineWithRates
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MachineValidationException
import com.mixvendas3d.app.domain.calculation.MachineValidator
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.CustomCardBrandRate
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.Machine
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MachineRepository {
    fun observeMachines(): Flow<List<Machine>>
    suspend fun createMachine(draft: MachineInputDraft): Machine
    suspend fun updateMachine(machineId: String, draft: MachineInputDraft): Machine
    suspend fun setCreditEnabled(machineId: String, creditEnabled: Boolean): Machine
    suspend fun deleteMachine(machineId: String)
}

class RoomMachineRepository(
    private val machineDao: MachineDao,
    private val dependencyCountProvider: suspend (String) -> Int = { 0 },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : MachineRepository {

    override fun observeMachines(): Flow<List<Machine>> {
        return machineDao.observeAllWithRates().map { rows ->
            rows.map { it.toDomain() }
        }
    }

    override suspend fun createMachine(draft: MachineInputDraft): Machine {
        val validated = MachineValidator.validateMachineInput(draft)
        val duplicate = machineDao.getByNormalizedName(validated.normalizedName)
        if (duplicate != null) {
            throw MachineValidationException(
                "Já existe uma maquininha cadastrada com o nome \"${duplicate.name}\".",
            )
        }

        val now = clockMillis()
        val machineId = idGenerator()
        val entity = MachineEntity(
            id = machineId,
            name = validated.name,
            normalizedName = validated.normalizedName,
            debitRateBps = validated.debitRateBps,
            creditEnabled = validated.creditEnabled,
            creditDefaultRateBps = validated.creditDefaultRateBps,
            brandRatesEnabled = validated.brandRatesEnabled,
            installmentsEnabled = validated.installmentsEnabled,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            maxInstallments = if (validated.clearMaxInstallments) null else validated.maxInstallments,
        )
        val standardBrands = (validated.brandRatesBps.keys + validated.brandDebitRatesBps.keys)
        val standardBrandEntities = standardBrands.map { brand ->
            MachineBrandRateEntity(
                machineId = machineId,
                brandKey = brand.storageKey,
                rateBps = validated.brandRatesBps[brand],
                debitRateBps = validated.brandDebitRatesBps[brand],
            )
        }
        val customBrandEntities = validated.customBrandRates.orEmpty().map { custom ->
            MachineBrandRateEntity(
                machineId = machineId,
                brandKey = custom.key,
                rateBps = custom.creditRateBps,
                debitRateBps = custom.debitRateBps,
            )
        }
        val brandEntities = standardBrandEntities + customBrandEntities
        val installmentEntities = validated.installmentAddonsBps.map { (tier, addonBps) ->
            MachineInstallmentRateEntity(
                machineId = machineId,
                installments = tier,
                addonBps = addonBps,
            )
        }

        machineDao.upsertMachineWithRates(
            machine = entity,
            brandRates = brandEntities,
            installmentRates = installmentEntities,
            isNew = true,
        )
        return machineDao.getWithRatesById(machineId)?.toDomain()
            ?: error("Maquininha recém-salva não encontrada no banco.")
    }

    override suspend fun updateMachine(machineId: String, draft: MachineInputDraft): Machine {
        val existing = machineDao.getWithRatesById(machineId)
            ?: throw MachineValidationException("Maquininha não encontrada para edição.")
        val validated = MachineValidator.validateMachineInput(draft)
        val duplicate = machineDao.getByNormalizedName(validated.normalizedName)
        if (duplicate != null && duplicate.id != machineId) {
            throw MachineValidationException(
                "Já existe outra maquininha cadastrada com o nome \"${duplicate.name}\".",
            )
        }

        val now = clockMillis()
        val resolvedMaxInstallments = when {
            validated.clearMaxInstallments -> null
            validated.maxInstallments != null -> validated.maxInstallments
            else -> existing.machine.maxInstallments
        }
        val updatedEntity = existing.machine.copy(
            name = validated.name,
            normalizedName = validated.normalizedName,
            debitRateBps = validated.debitRateBps,
            creditEnabled = validated.creditEnabled,
            creditDefaultRateBps = validated.creditDefaultRateBps,
            brandRatesEnabled = validated.brandRatesEnabled,
            installmentsEnabled = validated.installmentsEnabled,
            updatedAtEpochMs = now,
            maxInstallments = resolvedMaxInstallments,
        )
        val resolvedCustomBrands = validated.customBrandRates ?: run {
            val preserved = extractCustomBrandRates(existing.brandRates)
            MachineValidator.validateInstallmentCombinations(
                creditEnabled = validated.creditEnabled,
                installmentsEnabled = validated.installmentsEnabled,
                creditDefaultRateBps = validated.creditDefaultRateBps,
                standardBrandRatesBps = validated.brandRatesBps,
                customBrandRates = preserved,
                installmentAddonsBps = validated.installmentAddonsBps,
            )
            preserved
        }

        val standardBrands = (validated.brandRatesBps.keys + validated.brandDebitRatesBps.keys)
        val standardBrandEntities = standardBrands.map { brand ->
            MachineBrandRateEntity(
                machineId = machineId,
                brandKey = brand.storageKey,
                rateBps = validated.brandRatesBps[brand],
                debitRateBps = validated.brandDebitRatesBps[brand],
            )
        }
        val customBrandEntities = resolvedCustomBrands.map { custom ->
            MachineBrandRateEntity(
                machineId = machineId,
                brandKey = custom.key,
                rateBps = custom.creditRateBps,
                debitRateBps = custom.debitRateBps,
            )
        }
        val brandEntities = standardBrandEntities + customBrandEntities
        val installmentEntities = validated.installmentAddonsBps.map { (tier, addonBps) ->
            MachineInstallmentRateEntity(
                machineId = machineId,
                installments = tier,
                addonBps = addonBps,
            )
        }

        machineDao.upsertMachineWithRates(
            machine = updatedEntity,
            brandRates = brandEntities,
            installmentRates = installmentEntities,
            isNew = false,
        )
        return machineDao.getWithRatesById(machineId)?.toDomain()
            ?: error("Maquininha atualizada não encontrada no banco.")
    }

    override suspend fun setCreditEnabled(machineId: String, creditEnabled: Boolean): Machine {
        machineDao.getWithRatesById(machineId)
            ?: throw MachineValidationException("Maquininha não encontrada.")
        machineDao.updateCreditEnabled(
            machineId = machineId,
            creditEnabled = creditEnabled,
            updatedAtEpochMs = clockMillis(),
        )
        return machineDao.getWithRatesById(machineId)?.toDomain()
            ?: error("Maquininha atualizada não encontrada no banco.")
    }

    override suspend fun deleteMachine(machineId: String) {
        val existing = machineDao.getWithRatesById(machineId)
            ?: throw MachineValidationException("Maquininha não encontrada para exclusão.")
        val linkedCount = dependencyCountProvider(machineId)
        if (linkedCount > 0) {
            throw MachineValidationException(
                "Não é possível excluir \"${existing.machine.name}\" porque existem $linkedCount registro(s) vinculado(s).",
            )
        }
        val deleted = machineDao.deleteById(machineId)
        if (deleted == 0) {
            throw MachineValidationException("Não foi possível excluir a maquininha selecionada.")
        }
    }

    private fun MachineWithRates.toDomain(): Machine {
        val mappedBrands = linkedMapOf<CardBrand, Int>()
        val mappedDebitBrands = linkedMapOf<CardBrand, Int>()
        brandRates.forEach { entity ->
            val brand = CardBrand.fromStorageKey(entity.brandKey)
            if (brand != null) {
                if (entity.rateBps != null) mappedBrands[brand] = entity.rateBps
                if (entity.debitRateBps != null) mappedDebitBrands[brand] = entity.debitRateBps
            }
        }
        val mappedCustomBrands = extractCustomBrandRates(brandRates)
        val mappedInstallments = linkedMapOf<Int, Int>()
        InstallmentTiers.SUPPORTED.forEach { tier ->
            val saved = installmentRates.firstOrNull { it.installments == tier }?.addonBps
            if (saved != null) {
                mappedInstallments[tier] = saved
            }
        }
        return Machine(
            id = machine.id,
            name = machine.name,
            debitRateBps = machine.debitRateBps,
            creditEnabled = machine.creditEnabled,
            creditDefaultRateBps = machine.creditDefaultRateBps,
            brandRatesEnabled = machine.brandRatesEnabled,
            brandRatesBps = mappedBrands,
            brandDebitRatesBps = mappedDebitBrands,
            installmentsEnabled = machine.installmentsEnabled,
            installmentAddonsBps = mappedInstallments,
            createdAtEpochMs = machine.createdAtEpochMs,
            updatedAtEpochMs = machine.updatedAtEpochMs,
            maxInstallments = machine.maxInstallments,
            customBrandRates = mappedCustomBrands,
        )
    }

    private fun extractCustomBrandRates(
        brandRates: List<MachineBrandRateEntity>,
    ): List<CustomCardBrandRate> {
        return brandRates.mapNotNull { entity ->
            if (CardBrand.fromStorageKey(entity.brandKey) != null) {
                null
            } else {
                val decodedName = CustomBrandKeyCodec.decodeNameOrNull(entity.brandKey)
                if (decodedName != null) {
                    CustomCardBrandRate(
                        key = entity.brandKey,
                        name = decodedName,
                        creditRateBps = entity.rateBps,
                        debitRateBps = entity.debitRateBps,
                    )
                } else {
                    null
                }
            }
        }.sortedWith(
            compareBy<CustomCardBrandRate> { CustomBrandKeyCodec.normalizeBrandName(it.name) }
                .thenBy { it.name }
                .thenBy { it.key },
        )
    }
}
