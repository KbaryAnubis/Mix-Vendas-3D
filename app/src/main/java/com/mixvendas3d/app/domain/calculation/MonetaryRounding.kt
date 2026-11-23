package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.domain.models.MoneyCents
import java.math.BigInteger

class FinancialOverflowException(message: String) : ArithmeticException(message)

data class WeightedAllocationBucket(
    val key: String,
    val weight: Long,
)

/**
 * Utilitário determinístico de aritmética inteira e arredondamento monetário em centavos (Long),
 * utilizando BigInteger em produtos intermediários para proteção contra overflow, sem Float/Double.
 */
object MonetaryRounding {
    private val TEN_THOUSAND = BigInteger.valueOf(10_000L)
    private val FIVE_THOUSAND = BigInteger.valueOf(5_000L)

    fun safeAdd(a: Long, b: Long): Long {
        return try {
            Math.addExact(a, b)
        } catch (ex: ArithmeticException) {
            throw FinancialOverflowException("Soma monetária excede o limite suportado.")
        }
    }

    fun safeSubtract(a: Long, b: Long): Long {
        return try {
            Math.subtractExact(a, b)
        } catch (ex: ArithmeticException) {
            throw FinancialOverflowException("Subtração monetária excede o limite suportado.")
        }
    }

    fun safeMultiply(a: Long, b: Int): Long {
        return try {
            Math.multiplyExact(a, b.toLong())
        } catch (ex: ArithmeticException) {
            throw FinancialOverflowException("Multiplicação monetária excede o limite suportado.")
        }
    }

    fun safeAddOrNull(a: Long, b: Long): Long? {
        return try {
            safeAdd(a, b)
        } catch (_: FinancialOverflowException) {
            null
        }
    }

    fun safeMultiplyOrNull(a: Long, b: Int): Long? {
        return try {
            safeMultiply(a, b)
        } catch (_: FinancialOverflowException) {
            null
        }
    }

    /**
     * Calcula uma fração em pontos-base (1% = 100 bps, 100% = 10_000 bps)
     * utilizando arredondamento half-up puramente em inteiros, protegido contra overflow.
     */
    fun applyBasisPointsHalfUp(amount: MoneyCents, basisPoints: Int): MoneyCents {
        require(basisPoints >= 0) { "basisPoints deve ser maior ou igual a zero." }
        if (amount.rawCents == 0L || basisPoints == 0) return MoneyCents.ZERO

        val isNegative = amount.rawCents < 0L
        val absBig = BigInteger.valueOf(amount.rawCents).abs()
        val roundedBig = absBig
            .multiply(BigInteger.valueOf(basisPoints.toLong()))
            .add(FIVE_THOUSAND)
            .divide(TEN_THOUSAND)

        val roundedLong = try {
            if (isNegative) roundedBig.negate().longValueExact() else roundedBig.longValueExact()
        } catch (ex: ArithmeticException) {
            throw FinancialOverflowException("Resultado do cálculo de tarifa excede o limite suportado.")
        }
        return MoneyCents(roundedLong)
    }

    /**
     * Distribui exatamente [totalAmount] (>= 0) entre os [buckets] proporcionalmente aos respectivos
     * pesos (>= 0) pelo método dos maiores restos (Hamilton), com desempate determinístico por [key]
     * em ordem crescente — completamente independente da ordem de leitura do banco ou da lista.
     */
    fun allocateByWeightsDeterministic(
        totalAmount: Long,
        buckets: List<WeightedAllocationBucket>,
    ): Map<String, Long> {
        require(totalAmount >= 0L) { "totalAmount não pode ser negativo." }
        require(buckets.isNotEmpty()) { "Lista de alocação não pode ser vazia." }

        val seenKeys = HashSet<String>(buckets.size)
        var totalWeightBig = BigInteger.ZERO
        buckets.forEach { bucket ->
            require(seenKeys.add(bucket.key)) { "Chave duplicada na alocação: ${bucket.key}" }
            require(bucket.weight >= 0L) { "Peso negativo na alocação: ${bucket.key}" }
            totalWeightBig = totalWeightBig.add(BigInteger.valueOf(bucket.weight))
        }

        if (totalAmount == 0L) {
            return buckets.associate { it.key to 0L }
        }
        require(totalWeightBig > BigInteger.ZERO) {
            "A soma dos pesos precisa ser maior que zero para distribuir $totalAmount centavos."
        }

        val totalBig = BigInteger.valueOf(totalAmount)
        val baseAllocations = LinkedHashMap<String, Long>(buckets.size)
        data class RemainderCandidate(
            val key: String,
            val remainder: BigInteger,
        )

        val candidates = ArrayList<RemainderCandidate>(buckets.size)
        var allocatedSum = 0L

        buckets.forEach { bucket ->
            if (bucket.weight == 0L) {
                baseAllocations[bucket.key] = 0L
            } else {
                val numerator = totalBig.multiply(BigInteger.valueOf(bucket.weight))
                val (quotientBig, remainderBig) = numerator.divideAndRemainder(totalWeightBig)
                val quotient = try {
                    quotientBig.longValueExact()
                } catch (ex: ArithmeticException) {
                    throw FinancialOverflowException("Valor alocado excede o limite suportado.")
                }
                baseAllocations[bucket.key] = quotient
                allocatedSum = safeAdd(allocatedSum, quotient)
                if (remainderBig > BigInteger.ZERO) {
                    candidates.add(RemainderCandidate(key = bucket.key, remainder = remainderBig))
                }
            }
        }

        val remainingCents = safeSubtract(totalAmount, allocatedSum)
        if (remainingCents > 0L) {
            candidates.sortWith(
                Comparator { a, b ->
                    val remCmp = b.remainder.compareTo(a.remainder)
                    if (remCmp != 0) remCmp else a.key.compareTo(b.key)
                },
            )
            require(remainingCents <= candidates.size.toLong()) {
                "Inconsistência matemática: sobra ($remainingCents) maior que candidatos elegíveis (${candidates.size})."
            }
            for (index in 0 until remainingCents.toInt()) {
                val winnerKey = candidates[index].key
                baseAllocations[winnerKey] = safeAdd(baseAllocations.getValue(winnerKey), 1L)
            }
        }

        return baseAllocations
    }

    /**
     * Distribui exatamente [totalAmount] (>= 0) entre os [buckets] proporcionalmente aos respectivos
     * pesos (>= 0), respeitando o teto individual [CappedWeightedAllocationBucket.maxAmount] de cada
     * bucket por redistribuição iterativa (water-filling) determinística, sem perda ou sobra de centavos.
     */
    fun allocateByWeightsWithCapsDeterministic(
        totalAmount: Long,
        buckets: List<CappedWeightedAllocationBucket>,
    ): Map<String, Long> {
        require(totalAmount >= 0L) { "totalAmount não pode ser negativo." }
        require(buckets.isNotEmpty()) { "Lista de alocação não pode ser vazia." }

        val seenKeys = HashSet<String>(buckets.size)
        var totalCapBig = BigInteger.ZERO
        buckets.forEach { bucket ->
            require(seenKeys.add(bucket.key)) { "Chave duplicada na alocação: ${bucket.key}" }
            require(bucket.weight >= 0L) { "Peso negativo na alocação: ${bucket.key}" }
            require(bucket.maxAmount >= 0L) { "Teto negativo na alocação: ${bucket.key}" }
            totalCapBig = totalCapBig.add(BigInteger.valueOf(bucket.maxAmount))
        }

        val result = LinkedHashMap<String, Long>(buckets.size)
        buckets.forEach { result[it.key] = 0L }

        if (totalAmount == 0L) {
            return result
        }
        require(totalCapBig >= BigInteger.valueOf(totalAmount)) {
            "Capacidade total ($totalCapBig) insuficiente para alocar $totalAmount centavos."
        }

        var remaining = totalAmount
        val active = buckets.filter { it.maxAmount > 0L && it.weight > 0L }.toMutableList()

        while (remaining > 0L) {
            require(active.isNotEmpty()) {
                "Sem buckets ativos com peso positivo para alocar os $remaining centavos restantes."
            }
            val trial = allocateByWeightsDeterministic(
                totalAmount = remaining,
                buckets = active.map { WeightedAllocationBucket(key = it.key, weight = it.weight) },
            )
            val overflowed = active.filter { bucket ->
                trial.getValue(bucket.key) > bucket.maxAmount
            }
            if (overflowed.isEmpty()) {
                active.forEach { bucket ->
                    result[bucket.key] = safeAdd(result.getValue(bucket.key), trial.getValue(bucket.key))
                }
                remaining = 0L
            } else {
                overflowed.forEach { bucket ->
                    result[bucket.key] = bucket.maxAmount
                    remaining = safeSubtract(remaining, bucket.maxAmount)
                }
                val overflowKeys = overflowed.mapTo(HashSet(overflowed.size)) { it.key }
                active.removeAll { it.key in overflowKeys }
            }
        }

        return result
    }
}

data class CappedWeightedAllocationBucket(
    val key: String,
    val weight: Long,
    val maxAmount: Long,
)
