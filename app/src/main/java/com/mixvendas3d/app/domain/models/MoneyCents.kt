package com.mixvendas3d.app.domain.models

/**
 * Representação imutável de valores monetários em centavos inteiros (Long),
 * evitando erros de ponto flutuante (Float/Double) em cálculos financeiros futuros.
 */
@JvmInline
value class MoneyCents(val rawCents: Long) : Comparable<MoneyCents> {
    operator fun plus(other: MoneyCents): MoneyCents = MoneyCents(rawCents + other.rawCents)

    operator fun minus(other: MoneyCents): MoneyCents = MoneyCents(rawCents - other.rawCents)

    operator fun times(multiplier: Int): MoneyCents = MoneyCents(rawCents * multiplier)

    operator fun unaryMinus(): MoneyCents = MoneyCents(-rawCents)

    override fun compareTo(other: MoneyCents): Int = rawCents.compareTo(other.rawCents)

    companion object {
        val ZERO: MoneyCents = MoneyCents(0L)
    }
}
