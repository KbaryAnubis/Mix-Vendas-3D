package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["sold_at_epoch_ms"], name = "index_sales_sold_at_epoch_ms"),
    ],
)
data class SaleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "sold_at_epoch_ms")
    val soldAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String,
    @ColumnInfo(name = "original_subtotal_cents")
    val originalSubtotalCents: Long,
    @ColumnInfo(name = "discount_total_cents")
    val discountTotalCents: Long,
    @ColumnInfo(name = "final_amount_cents")
    val finalAmountCents: Long,
    @ColumnInfo(name = "fee_total_cents")
    val feeTotalCents: Long,
    @ColumnInfo(name = "net_total_cents")
    val netTotalCents: Long,
    @ColumnInfo(name = "discount_mode")
    val discountMode: String,
    @ColumnInfo(name = "discount_target_product_id")
    val discountTargetProductId: String?,
    @ColumnInfo(name = "discount_target_product_name")
    val discountTargetProductName: String?,
    @ColumnInfo(name = "machine_id")
    val machineId: String?,
    @ColumnInfo(name = "machine_name")
    val machineName: String?,
    @ColumnInfo(name = "machine_debit_rate_bps")
    val machineDebitRateBps: Int?,
    @ColumnInfo(name = "machine_credit_default_rate_bps")
    val machineCreditDefaultRateBps: Int?,
    @ColumnInfo(name = "machine_brand_rates_enabled")
    val machineBrandRatesEnabled: Boolean?,
    @ColumnInfo(name = "machine_installments_enabled")
    val machineInstallmentsEnabled: Boolean?,
    @ColumnInfo(name = "card_brand")
    val cardBrand: String?,
    @ColumnInfo(name = "installments")
    val installments: Int?,
    @ColumnInfo(name = "automatic_rate_bps")
    val automaticRateBps: Int?,
    @ColumnInfo(name = "manual_rate_override_bps")
    val manualRateOverrideBps: Int?,
    @ColumnInfo(name = "effective_fee_rate_bps")
    val effectiveFeeRateBps: Int,
    @ColumnInfo(name = "note")
    val note: String,
)
