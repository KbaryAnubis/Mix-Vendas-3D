package com.mixvendas3d.app.ui.screens

import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewResult
import com.mixvendas3d.app.domain.export.MergeUnitType
import com.mixvendas3d.app.domain.export.PreviewChangeOperation
import com.mixvendas3d.app.domain.export.PreviewChangedEntry
import com.mixvendas3d.app.domain.export.PreviewFinancialSummary
import com.mixvendas3d.app.domain.export.ValidatedBackupData
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter

enum class BackupPreviewPhase {
    IDLE,
    PREPARING,
    AVAILABLE,
    INVALID,
    LOCAL_STATE_CHANGED,
    VALIDATION_ERROR,
}

enum class BackupPreviewGroupType(
    val key: String,
    val title: String,
) {
    PEOPLE("PEOPLE", "Pessoas"),
    PRODUCT("PRODUCT", "Produtos"),
    MACHINE("MACHINE", "Maquininhas"),
    SALE("SALE", "Vendas"),
    PREFERENCES("PREFERENCES", "Preferências"),
}

data class BackupPreviewChangeItemUi(
    val entryKey: String,
    val operation: PreviewChangeOperation,
    val isAtomicUnit: Boolean,
    val title: String,
    val subtitle: String,
    val atomicReplacementNote: String?,
    val detailLines: List<String>,
)

data class BackupPreviewChangeGroupUi(
    val groupType: BackupPreviewGroupType,
    val entries: List<BackupPreviewChangeItemUi>,
)

data class BackupPreviewFinancialUi(
    val localSalesCount: Int,
    val finalSalesCount: Int,
    val addedSalesCount: Int,
    val replacedSalesCount: Int,
    val localTotalFinalBrl: String,
    val finalTotalFinalBrl: String,
    val localTotalFeeBrl: String,
    val finalTotalFeeBrl: String,
    val localTotalNetBrl: String,
    val finalTotalNetBrl: String,
    val addedSalesFinalBrl: String,
    val addedSalesFeeBrl: String,
    val addedSalesNetBrl: String,
    val replacedSalesFinalDeltaBrl: String,
    val replacedSalesFeeDeltaBrl: String,
    val replacedSalesNetDeltaBrl: String,
    val netFinalAmountDeltaBrl: String,
    val netFeeAmountDeltaBrl: String,
    val netNetAmountDeltaBrl: String,
    val netFinalAmountDeltaCents: Long,
    val netFeeAmountDeltaCents: Long,
    val netNetAmountDeltaCents: Long,
    val replacedSalesFinalDeltaCents: Long,
    val replacedSalesNetDeltaCents: Long,
)

data class BackupPreviewUi(
    val exportedAtEpochMs: Long,
    val formattedBackupDate: String,
    val peopleToAddCount: Int,
    val productsToAddCount: Int,
    val machinesToAddCount: Int,
    val salesToAddCount: Int,
    val preferencesToAddCount: Int,
    val newRecordsCount: Int,
    val keptRecordsCount: Int,
    val ignoredRecordsCount: Int,
    val unitsToReplaceCount: Int,
    val localOnlyPreservedCount: Int,
    val finalTotalRecordsCount: Int,
    val finalPeopleCount: Int,
    val finalProductsCount: Int,
    val finalMachinesCount: Int,
    val finalSalesCount: Int,
    val finalSaleItemsCount: Int,
    val finalPreferencesCount: Int,
    val changeGroups: List<BackupPreviewChangeGroupUi>,
    val financialSummary: BackupPreviewFinancialUi,
    val authorizedOperationsCount: Int,
)

internal object BackupPreviewFormatter {

    fun buildPreviewUi(
        validatedBackup: ValidatedBackupData,
        preview: JsonBackupMergePreviewResult,
    ): BackupPreviewUi {
        val merged = preview.mergedCollections
        val peopleById = (merged.people + validatedBackup.people).associateBy { it.id }
        val productsById = (merged.products + validatedBackup.products).associateBy { it.id }
        val productPartsByProd = merged.productParticipants.groupBy { it.productId }
        val machinesById = (merged.machines + validatedBackup.machines).associateBy { it.id }
        val machineBrandsByMach = merged.machineBrandRates.groupBy { it.machineId }
        val machineInstByMach = merged.machineInstallmentRates.groupBy { it.machineId }
        val salesById = (merged.sales + validatedBackup.sales).associateBy { it.id }
        val saleItemsBySale = merged.saleItems.groupBy { it.saleId }
        val saleTotalsBySale = merged.salePersonTotals.groupBy { it.saleId }
        val prefsByKey = (merged.preferences + validatedBackup.preferences).associateBy { it.key }

        val groupedItems = linkedMapOf<BackupPreviewGroupType, MutableList<BackupPreviewChangeItemUi>>()
        BackupPreviewGroupType.entries.forEach { groupedItems[it] = mutableListOf() }

        preview.changedEntries.forEach { entry ->
            val groupType = groupTypeFor(entry)
            val uiItem = formatChangedEntry(
                entry = entry,
                peopleById = peopleById,
                productsById = productsById,
                productPartsByProd = productPartsByProd,
                machinesById = machinesById,
                machineBrandsByMach = machineBrandsByMach,
                machineInstByMach = machineInstByMach,
                salesById = salesById,
                saleItemsBySale = saleItemsBySale,
                saleTotalsBySale = saleTotalsBySale,
                prefsByKey = prefsByKey,
            )
            groupedItems.getValue(groupType).add(uiItem)
        }

        val nonEmptyGroups = BackupPreviewGroupType.entries.mapNotNull { groupType ->
            val items = groupedItems.getValue(groupType)
            if (items.isEmpty()) null else BackupPreviewChangeGroupUi(groupType = groupType, entries = items)
        }

        val finalCounts = preview.finalTableCounts
        val entries = preview.changedEntries

        return BackupPreviewUi(
            exportedAtEpochMs = validatedBackup.exportedAtEpochMs,
            formattedBackupDate = CurrencyFormatter.formatEpochMillis(validatedBackup.exportedAtEpochMs),
            peopleToAddCount = entries.count {
                it.collection == BackupCollectionType.PEOPLE && it.operation == PreviewChangeOperation.ADD
            },
            productsToAddCount = entries.count {
                it.unitType == MergeUnitType.PRODUCT && it.operation == PreviewChangeOperation.ADD
            },
            machinesToAddCount = entries.count {
                it.unitType == MergeUnitType.MACHINE && it.operation == PreviewChangeOperation.ADD
            },
            salesToAddCount = entries.count {
                it.unitType == MergeUnitType.SALE && it.operation == PreviewChangeOperation.ADD
            },
            preferencesToAddCount = entries.count {
                it.collection == BackupCollectionType.PREFERENCES && it.operation == PreviewChangeOperation.ADD
            },
            newRecordsCount = preview.newRecordsCount,
            keptRecordsCount = preview.keptRecordsCount,
            ignoredRecordsCount = preview.ignoredRecordsCount,
            unitsToReplaceCount = preview.unitsToReplaceCount,
            localOnlyPreservedCount = preview.localOnlyPreservedCount,
            finalTotalRecordsCount = finalCounts.values.sum(),
            finalPeopleCount = finalCounts[BackupCollectionType.PEOPLE] ?: 0,
            finalProductsCount = finalCounts[BackupCollectionType.PRODUCTS] ?: 0,
            finalMachinesCount = finalCounts[BackupCollectionType.MACHINES] ?: 0,
            finalSalesCount = finalCounts[BackupCollectionType.SALES] ?: 0,
            finalSaleItemsCount = finalCounts[BackupCollectionType.SALE_ITEMS] ?: 0,
            finalPreferencesCount = finalCounts[BackupCollectionType.PREFERENCES] ?: 0,
            changeGroups = nonEmptyGroups,
            financialSummary = formatFinancialSummary(preview.financialSummary),
            authorizedOperationsCount = preview.executionContract.authorizedOperationsCount,
        )
    }

    private fun groupTypeFor(entry: PreviewChangedEntry): BackupPreviewGroupType {
        return when {
            entry.collection == BackupCollectionType.PEOPLE -> BackupPreviewGroupType.PEOPLE
            entry.unitType == MergeUnitType.PRODUCT -> BackupPreviewGroupType.PRODUCT
            entry.unitType == MergeUnitType.MACHINE -> BackupPreviewGroupType.MACHINE
            entry.unitType == MergeUnitType.SALE -> BackupPreviewGroupType.SALE
            entry.collection == BackupCollectionType.PREFERENCES -> BackupPreviewGroupType.PREFERENCES
            else -> BackupPreviewGroupType.PREFERENCES
        }
    }

    private fun formatChangedEntry(
        entry: PreviewChangedEntry,
        peopleById: Map<String, com.mixvendas3d.app.domain.export.PersonDto>,
        productsById: Map<String, com.mixvendas3d.app.domain.export.ProductDto>,
        productPartsByProd: Map<String, List<com.mixvendas3d.app.domain.export.ProductParticipantDto>>,
        machinesById: Map<String, com.mixvendas3d.app.domain.export.MachineDto>,
        machineBrandsByMach: Map<String, List<com.mixvendas3d.app.domain.export.MachineBrandRateDto>>,
        machineInstByMach: Map<String, List<com.mixvendas3d.app.domain.export.MachineInstallmentRateDto>>,
        salesById: Map<String, com.mixvendas3d.app.domain.export.SaleDto>,
        saleItemsBySale: Map<String, List<com.mixvendas3d.app.domain.export.SaleItemDto>>,
        saleTotalsBySale: Map<String, List<com.mixvendas3d.app.domain.export.SalePersonTotalDto>>,
        prefsByKey: Map<String, com.mixvendas3d.app.domain.export.PreferenceDto>,
    ): BackupPreviewChangeItemUi {
        val opVerb = if (entry.operation == PreviewChangeOperation.ADD) "Incluir" else "Substituir pelo backup"
        val entryKey = "${entry.operation.name}_${entry.targetId}"

        return when {
            entry.collection == BackupCollectionType.PEOPLE -> {
                val person = peopleById[entry.targetId]
                BackupPreviewChangeItemUi(
                    entryKey = entryKey,
                    operation = entry.operation,
                    isAtomicUnit = false,
                    title = person?.name ?: entry.targetId,
                    subtitle = "$opVerb · Pessoa",
                    atomicReplacementNote = if (entry.operation == PreviewChangeOperation.REPLACE) {
                        "O cadastro desta pessoa no celular será atualizado pelos dados do backup."
                    } else {
                        null
                    },
                    detailLines = listOfNotNull(
                        person?.let { "Nome: ${it.name}" },
                    ),
                )
            }

            entry.unitType == MergeUnitType.PRODUCT -> {
                val product = productsById[entry.targetId]
                val partsCount = entry.memberCountsByCollection[BackupCollectionType.PRODUCT_PARTICIPANTS]
                    ?: productPartsByProd[entry.targetId].orEmpty().size
                val splitsText = productPartsByProd[entry.targetId].orEmpty()
                    .sortedBy { it.sortOrder }
                    .joinToString(" · ") { part ->
                        val pName = peopleById[part.personId]?.name ?: part.personId
                        "$pName ${formatMoney(part.amountCents)}"
                    }
                BackupPreviewChangeItemUi(
                    entryKey = entryKey,
                    operation = entry.operation,
                    isAtomicUnit = true,
                    title = product?.name ?: entry.targetId,
                    subtitle = "$opVerb · Produto ($partsCount participante(s))",
                    atomicReplacementNote = if (entry.operation == PreviewChangeOperation.REPLACE) {
                        "Conjunto único: o produto e seus $partsCount participante(s) serão substituídos conjuntamente, sem misturar divisões locais."
                    } else {
                        null
                    },
                    detailLines = buildList {
                        product?.let {
                            add("Preço: ${formatMoney(it.priceCents)} · ${if (it.active) "Ativo" else "Inativo"}")
                        }
                        if (splitsText.isNotBlank()) {
                            add("Divisão: $splitsText")
                        }
                    },
                )
            }

            entry.unitType == MergeUnitType.MACHINE -> {
                val machine = machinesById[entry.targetId]
                val brandsCount = entry.memberCountsByCollection[BackupCollectionType.MACHINE_BRAND_RATES]
                    ?: machineBrandsByMach[entry.targetId].orEmpty().size
                val instCount = entry.memberCountsByCollection[BackupCollectionType.MACHINE_INSTALLMENT_RATES]
                    ?: machineInstByMach[entry.targetId].orEmpty().size
                BackupPreviewChangeItemUi(
                    entryKey = entryKey,
                    operation = entry.operation,
                    isAtomicUnit = true,
                    title = machine?.name ?: entry.targetId,
                    subtitle = "$opVerb · Maquininha ($brandsCount bandeira(s), $instCount parcelamento(s))",
                    atomicReplacementNote = if (entry.operation == PreviewChangeOperation.REPLACE) {
                        "Conjunto único: a maquininha e todas as taxas relacionadas ($brandsCount por bandeira e $instCount de parcelamento) serão substituídas conjuntamente."
                    } else {
                        null
                    },
                    detailLines = buildList {
                        machine?.let {
                            add("Débito: ${RateFormatter.formatBpsAsPercent(it.debitRateBps)}")
                            val creditDesc = if (it.creditEnabled) {
                                RateFormatter.formatBpsAsPercent(it.creditDefaultRateBps)
                            } else {
                                "Desativado"
                            }
                            add("Crédito à vista: $creditDesc")
                        }
                    },
                )
            }

            entry.unitType == MergeUnitType.SALE -> {
                val sale = salesById[entry.targetId]
                val itemsCount = entry.memberCountsByCollection[BackupCollectionType.SALE_ITEMS]
                    ?: saleItemsBySale[entry.targetId].orEmpty().size
                val sipsCount = entry.memberCountsByCollection[BackupCollectionType.SALE_ITEM_PARTICIPANTS] ?: 0
                val totalsCount = entry.memberCountsByCollection[BackupCollectionType.SALE_PERSON_TOTALS]
                    ?: saleTotalsBySale[entry.targetId].orEmpty().size
                val saleTitle = if (sale != null) {
                    "Venda de ${CurrencyFormatter.formatEpochMillis(sale.soldAtEpochMs)}"
                } else {
                    "Venda ${entry.targetId}"
                }
                val itemsSummary = saleItemsBySale[entry.targetId].orEmpty()
                    .sortedBy { it.sortOrder }
                    .joinToString(", ") { "${it.quantity}x ${it.productName}" }
                val methodLabel = sale?.let {
                    val base = SalePaymentMethod.fromStorageKey(it.paymentMethod)?.label ?: it.paymentMethod
                    if (!it.machineName.isNullOrBlank()) "$base · ${it.machineName}" else base
                }

                BackupPreviewChangeItemUi(
                    entryKey = entryKey,
                    operation = entry.operation,
                    isAtomicUnit = true,
                    title = saleTitle,
                    subtitle = "$opVerb · Venda histórica ($itemsCount item(ns))",
                    atomicReplacementNote = if (entry.operation == PreviewChangeOperation.REPLACE) {
                        "Conjunto financeiro indivisível: a venda e seus itens ($itemsCount), participantes ($sipsCount) e totais históricos ($totalsCount) serão substituídos conjuntamente, preservando snapshots sem recálculo."
                    } else {
                        null
                    },
                    detailLines = buildList {
                        sale?.let {
                            add(
                                "Valor: ${formatMoney(it.finalAmountCents)} · " +
                                    "Tarifa: ${formatMoney(it.feeTotalCents)} · " +
                                    "Líquido: ${formatMoney(it.netTotalCents)}",
                            )
                            if (it.discountTotalCents > 0L) {
                                add("Desconto aplicado: ${formatMoney(it.discountTotalCents)}")
                            }
                        }
                        if (!methodLabel.isNullOrBlank()) {
                            add("Pagamento: $methodLabel")
                        }
                        if (itemsSummary.isNotBlank()) {
                            add("Itens: $itemsSummary")
                        }
                    },
                )
            }

            else -> {
                val pref = prefsByKey[entry.targetId]
                val friendlyName = if (entry.targetId == "app_theme_mode") {
                    "Tema do aplicativo"
                } else {
                    entry.targetId
                }
                BackupPreviewChangeItemUi(
                    entryKey = entryKey,
                    operation = entry.operation,
                    isAtomicUnit = false,
                    title = friendlyName,
                    subtitle = "$opVerb · Preferência",
                    atomicReplacementNote = if (entry.operation == PreviewChangeOperation.REPLACE) {
                        "A preferência local será substituída pelo valor salvo no backup."
                    } else {
                        null
                    },
                    detailLines = listOfNotNull(
                        pref?.let { "Valor: ${it.value}" },
                    ),
                )
            }
        }
    }

    private fun formatFinancialSummary(summary: PreviewFinancialSummary): BackupPreviewFinancialUi {
        return BackupPreviewFinancialUi(
            localSalesCount = summary.localSalesCount,
            finalSalesCount = summary.finalSalesCount,
            addedSalesCount = summary.addedSalesCount,
            replacedSalesCount = summary.replacedSalesCount,
            localTotalFinalBrl = formatMoney(summary.localTotalFinalAmountCents),
            finalTotalFinalBrl = formatMoney(summary.finalTotalFinalAmountCents),
            localTotalFeeBrl = formatMoney(summary.localTotalFeeAmountCents),
            finalTotalFeeBrl = formatMoney(summary.finalTotalFeeAmountCents),
            localTotalNetBrl = formatMoney(summary.localTotalNetAmountCents),
            finalTotalNetBrl = formatMoney(summary.finalTotalNetAmountCents),
            addedSalesFinalBrl = formatSignedMoney(summary.addedSalesFinalAmountCents),
            addedSalesFeeBrl = formatMoney(summary.addedSalesFeeAmountCents),
            addedSalesNetBrl = formatSignedMoney(summary.addedSalesNetAmountCents),
            replacedSalesFinalDeltaBrl = formatSignedMoney(summary.replacedSalesFinalDeltaCents),
            replacedSalesFeeDeltaBrl = formatSignedMoney(summary.replacedSalesFeeDeltaCents),
            replacedSalesNetDeltaBrl = formatSignedMoney(summary.replacedSalesNetDeltaCents),
            netFinalAmountDeltaBrl = formatSignedMoney(summary.netFinalAmountDeltaCents),
            netFeeAmountDeltaBrl = formatSignedMoney(summary.netFeeAmountDeltaCents),
            netNetAmountDeltaBrl = formatSignedMoney(summary.netNetAmountDeltaCents),
            netFinalAmountDeltaCents = summary.netFinalAmountDeltaCents,
            netFeeAmountDeltaCents = summary.netFeeAmountDeltaCents,
            netNetAmountDeltaCents = summary.netNetAmountDeltaCents,
            replacedSalesFinalDeltaCents = summary.replacedSalesFinalDeltaCents,
            replacedSalesNetDeltaCents = summary.replacedSalesNetDeltaCents,
        )
    }

    fun formatMoney(cents: Long): String {
        return CurrencyFormatter.formatBrl(MoneyCents(cents))
    }

    fun formatSignedMoney(cents: Long): String {
        val base = CurrencyFormatter.formatBrl(MoneyCents(cents))
        return if (cents > 0L) "+$base" else base
    }
}
