package com.mixvendas3d.app.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mixvendas3d.app.data.preview.PreviewFixtures
import com.mixvendas3d.app.data.repository.CatalogRepository
import com.mixvendas3d.app.data.repository.MachineRepository
import com.mixvendas3d.app.data.repository.PreferenceRepository
import com.mixvendas3d.app.data.repository.SaleRepository
import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.calculation.SaleCalculationException
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.export.CsvExportPeriod
import com.mixvendas3d.app.domain.export.CsvExportResult
import com.mixvendas3d.app.domain.export.CsvExportValidationException
import com.mixvendas3d.app.domain.export.CsvSaleExporter
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.Person
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.domain.models.PreviewPeriodFilter
import com.mixvendas3d.app.domain.models.PreviewPerson
import com.mixvendas3d.app.domain.models.PreviewPersonSummary
import com.mixvendas3d.app.domain.models.PreviewSale
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductCategory
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SaleMachineSnapshot
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot
import com.mixvendas3d.app.domain.models.toSnapshotMachine
import com.mixvendas3d.app.domain.models.toSnapshotProduct
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.domain.reporting.RealDailySalesReport
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DaySalesPreviewGroup(
    val date: LocalDate,
    val count: Int,
    val gross: MoneyCents,
)

data class MonthSalesPreviewGroup(
    val yearMonth: YearMonth,
    val label: String,
    val days: List<DaySalesPreviewGroup>,
    val gross: MoneyCents,
    val count: Int,
)

data class PaymentMethodBreakdown(
    val method: PreviewPaymentMethod,
    val count: Int,
    val gross: MoneyCents,
    val fee: MoneyCents,
    val net: MoneyCents,
)

data class MachineBreakdown(
    val machineName: String,
    val count: Int,
    val gross: MoneyCents,
    val fee: MoneyCents,
    val net: MoneyCents,
)

data class RealPaymentMethodBreakdown(
    val method: SalePaymentMethod,
    val count: Int,
    val gross: MoneyCents,
    val fee: MoneyCents,
    val net: MoneyCents,
)

data class RealMachineBreakdown(
    val machineKey: String,
    val machineId: String?,
    val machineName: String,
    val paymentMethod: SalePaymentMethod,
    val count: Int,
    val gross: MoneyCents,
    val fee: MoneyCents,
    val net: MoneyCents,
    val distinctEffectiveRatesBps: List<Int>,
    val hasManualOverride: Boolean,
    val singleRateSummaryLabel: String?,
)

data class VenderFormUiState(
    val editingSaleId: String? = null,
    val originalEditingSale: PersistedSale? = null,
    val historicalProductsById: Map<String, Product> = emptyMap(),
    val historicalMachineSnapshot: SaleMachineSnapshot? = null,
    val preferHistoricalMachineSnapshot: Boolean = false,
    val selectedQuantities: Map<String, Int> = emptyMap(),
    val pickerOpen: Boolean = false,
    val pickerSearchQuery: String = "",
    val finalAmountInput: String = "",
    val finalAmountTouched: Boolean = false,
    val discountTargetProductId: String = DISCOUNT_PROPORTIONAL,
    val selectedMethod: PreviewPaymentMethod = PreviewPaymentMethod.PIX,
    val selectedMachineId: String? = null,
    val machinePickerOpen: Boolean = false,
    val machinePickerSearchQuery: String = "",
    val selectedBrand: CardBrand = CardBrand.VISA,
    val selectedInstallments: Int = 1,
    val feeOverrideEnabled: Boolean = false,
    val manualRateInput: String = "",
    val note: String = "",
    val recalculatedNotice: String? = null,
    val recalculatedNoticeIsError: Boolean = false,
    val showDiscardEditDialog: Boolean = false,
    val isSaving: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
    val selectedBrandKey: String = CardBrand.VISA.storageKey,
) {
    val isEditing: Boolean
        get() = editingSaleId != null

    val isDiscountTargetMissing: Boolean
        get() = discountTargetProductId != DISCOUNT_PROPORTIONAL &&
            (selectedQuantities[discountTargetProductId] ?: 0) <= 0

    fun resolvedBrandKey(): String = selectedBrandKey.ifBlank { selectedBrand.storageKey }

    fun resolvedBrandDisplayLabel(): String =
        CustomBrandKeyCodec.resolveDisplayLabel(resolvedBrandKey()) ?: selectedBrand.label

    companion object {
        const val DISCOUNT_PROPORTIONAL = "proportional"
    }
}

data class SaleDeleteUiState(
    val targetSale: PersistedSale? = null,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
) {
    val isDialogVisible: Boolean
        get() = targetSale != null
}

data class MixVendasShellUiState(
    val roomPreferencesCount: Int = 0,
    val previewEnabled: Boolean = true,
    val periodFilter: PreviewPeriodFilter = PreviewPeriodFilter.ALL,
    val periodFromDate: String = "",
    val periodToDate: String = "",
    val showAllPeopleOnHome: Boolean = false,
    val salesSearchQuery: String = "",
    val salesMethodFilter: PreviewPaymentMethod? = null,
    // Estados demonstrativos preservados apenas para Início e Fechamento (Etapas posteriores)
    val people: List<PreviewPerson> = PreviewFixtures.people,
    val activeCatalogProducts: List<Product> = emptyList(),
    val catalogProductCategories: List<ProductCategory> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val filteredSales: List<PreviewSale> = emptyList(),
    val totalGross: MoneyCents = MoneyCents.ZERO,
    val totalFee: MoneyCents = MoneyCents.ZERO,
    val totalNet: MoneyCents = MoneyCents.ZERO,
    val personSummaries: List<PreviewPersonSummary> = emptyList(),
    val paymentBreakdowns: List<PaymentMethodBreakdown> = emptyList(),
    val machineBreakdowns: List<MachineBreakdown> = emptyList(),
    val dayGroups: List<DaySalesPreviewGroup> = emptyList(),
    val monthGroups: List<MonthSalesPreviewGroup> = emptyList(),
    // Estados 100% reais da aba Início (Etapa 4B-1)
    val homePeriodFilter: PreviewPeriodFilter = PreviewPeriodFilter.ALL,
    val homePeriodFromDate: String = "",
    val homePeriodToDate: String = "",
    val homeRealTotalSalesCount: Int = 0,
    val homeFilteredRealSales: List<PersistedSale> = emptyList(),
    val homeFilteredRealSalesCount: Int = 0,
    val homeRealTotalGross: MoneyCents = MoneyCents.ZERO,
    val homeRealTotalFee: MoneyCents = MoneyCents.ZERO,
    val homeRealTotalNet: MoneyCents = MoneyCents.ZERO,
    val homeRealPersonSummaries: List<SalePersonTotalSnapshot> = emptyList(),
    val homeRealPersonSalesCounts: Map<String, Int> = emptyMap(),
    val homeDailyReport: RealDailySalesReport = RealDailySalesReport(),
    // Estados 100% reais da aba Fechamento (Etapa 4B-2)
    val closingPeriodFilter: PreviewPeriodFilter = PreviewPeriodFilter.ALL,
    val closingPeriodFromDate: String = "",
    val closingPeriodToDate: String = "",
    val closingRealTotalSalesCount: Int = 0,
    val closingFilteredRealSales: List<PersistedSale> = emptyList(),
    val closingFilteredRealSalesCount: Int = 0,
    val closingRealOriginalSubtotal: MoneyCents = MoneyCents.ZERO,
    val closingRealTotalDiscount: MoneyCents = MoneyCents.ZERO,
    val closingRealTotalGross: MoneyCents = MoneyCents.ZERO,
    val closingRealTotalFee: MoneyCents = MoneyCents.ZERO,
    val closingRealTotalNet: MoneyCents = MoneyCents.ZERO,
    val closingRealPersonSummaries: List<SalePersonTotalSnapshot> = emptyList(),
    val closingRealPaymentBreakdowns: List<RealPaymentMethodBreakdown> = emptyList(),
    val closingRealMachineBreakdowns: List<RealMachineBreakdown> = emptyList(),
    // Estados 100% reais da aba VENDAS (Etapa 3B-2A+)
    val realSalesCount: Int = 0,
    val filteredRealSales: List<PersistedSale> = emptyList(),
    val realTotalGross: MoneyCents = MoneyCents.ZERO,
    val realTotalDiscount: MoneyCents = MoneyCents.ZERO,
    val realTotalFee: MoneyCents = MoneyCents.ZERO,
    val realTotalNet: MoneyCents = MoneyCents.ZERO,
    val realPersonSummaries: List<SalePersonTotalSnapshot> = emptyList(),
    val realDailyReport: RealDailySalesReport = RealDailySalesReport(),
    val selectedSalesDate: LocalDate? = null,
    val saleDeleteState: SaleDeleteUiState = SaleDeleteUiState(),
    val salesFeedbackMessage: String? = null,
    val salesFeedbackIsError: Boolean = false,
    val closingCsvExportState: ClosingCsvExportUiState = ClosingCsvExportUiState(),
    val venderForm: VenderFormUiState = VenderFormUiState(),
)

private data class CsvExportSession(
    val mode: CsvExportMode,
    val result: CsvExportResult,
    val summarySaved: Boolean = false,
    val detailedSaved: Boolean = false,
)

private data class SalesActionState(
    val deleteState: SaleDeleteUiState = SaleDeleteUiState(),
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
)

private data class FilterState(
    val previewEnabled: Boolean = true,
    val periodFilter: PreviewPeriodFilter = PreviewPeriodFilter.ALL,
    val periodFromDate: String = "",
    val periodToDate: String = "",
    val homePeriodFilter: PreviewPeriodFilter = PreviewPeriodFilter.ALL,
    val homePeriodFromDate: String = "",
    val homePeriodToDate: String = "",
    val closingPeriodFilter: PreviewPeriodFilter = PreviewPeriodFilter.ALL,
    val closingPeriodFromDate: String = "",
    val closingPeriodToDate: String = "",
    val showAllPeopleOnHome: Boolean = false,
    val salesSearchQuery: String = "",
    val salesMethodFilter: PreviewPaymentMethod? = null,
    val selectedSalesDate: LocalDate? = null,
)

private data class CatalogAndMachineBundle(
    val prefCount: Int,
    val people: List<Person>,
    val activeProducts: List<Product>,
    val categories: List<ProductCategory>,
    val machines: List<Machine>,
    val realSales: List<PersistedSale>,
    val realDailyReport: RealDailySalesReport,
)

class MixVendasShellViewModel(
    preferenceRepository: PreferenceRepository,
    catalogRepository: CatalogRepository,
    machineRepository: MachineRepository,
    private val saleRepository: SaleRepository,
) : ViewModel() {

    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")
    private val basePreviewSales: List<PreviewSale> = PreviewFixtures.buildPreviewSales()
    private val filterStateFlow = MutableStateFlow(FilterState())
    private val venderFormFlow = MutableStateFlow(VenderFormUiState())
    private val salesActionFlow = MutableStateFlow(SalesActionState())
    private val csvExportStateFlow = MutableStateFlow(ClosingCsvExportUiState())
    private val isSavingLock = AtomicBoolean(false)
    private val isDeletingLock = AtomicBoolean(false)
    private val isCsvExportLock = AtomicBoolean(false)

    @Volatile
    private var activeCsvExportSession: CsvExportSession? = null

    @Volatile
    private var latestActiveProducts: List<Product> = emptyList()

    @Volatile
    private var latestMachines: List<Machine> = emptyList()

    @Volatile
    private var latestRealSales: List<PersistedSale> = emptyList()

    private val dataBundleFlow = combine(
        preferenceRepository.observePreferenceCount().onStart { emit(0) },
        catalogRepository.observePeople().onStart { emit(emptyList()) },
        catalogRepository.observeActiveProducts().onStart { emit(emptyList()) },
        catalogRepository.observeProductCategories().onStart { emit(emptyList()) },
        machineRepository.observeMachines().onStart { emit(emptyList()) },
        saleRepository.observeSales().onStart { emit(emptyList()) },
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        CatalogAndMachineBundle(
            prefCount = flows[0] as Int,
            people = flows[1] as List<Person>,
            activeProducts = flows[2] as List<Product>,
            categories = flows[3] as List<ProductCategory>,
            machines = flows[4] as List<Machine>,
            realSales = flows[5] as List<PersistedSale>,
            realDailyReport = DailySalesAggregator.aggregate(flows[5] as List<PersistedSale>),
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<MixVendasShellUiState> = combine(
        dataBundleFlow,
        filterStateFlow,
        venderFormFlow,
        salesActionFlow,
        csvExportStateFlow,
    ) { bundle, filter, venderForm, salesAction, csvExport ->
        latestActiveProducts = bundle.activeProducts
        latestMachines = bundle.machines
        latestRealSales = bundle.realSales
        val syncedForm = syncForm(venderForm, bundle.activeProducts, bundle.machines)
        buildUiState(
            roomPrefCount = bundle.prefCount,
            registeredPeople = bundle.people,
            activeCatalogProducts = bundle.activeProducts,
            catalogProductCategories = bundle.categories,
            machines = bundle.machines,
            realSales = bundle.realSales,
            realDailyReport = bundle.realDailyReport,
            filter = filter,
            venderForm = syncedForm,
            salesAction = salesAction,
            closingCsvExport = csvExport,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = buildUiState(
            roomPrefCount = 0,
            registeredPeople = emptyList(),
            activeCatalogProducts = emptyList(),
            machines = emptyList(),
            realSales = emptyList(),
            realDailyReport = RealDailySalesReport(),
            filter = FilterState(),
            venderForm = VenderFormUiState(),
            salesAction = SalesActionState(),
            closingCsvExport = ClosingCsvExportUiState(),
        ),
    )

    fun togglePreviewData() {
        val current = filterStateFlow.value
        filterStateFlow.value = current.copy(previewEnabled = !current.previewEnabled)
    }

    fun onHomePeriodFilterSelected(period: PreviewPeriodFilter) {
        filterStateFlow.value = filterStateFlow.value.copy(
            homePeriodFilter = period,
        )
    }

    fun onHomeCustomDatesChanged(from: String, to: String) {
        filterStateFlow.value = filterStateFlow.value.copy(
            homePeriodFromDate = from,
            homePeriodToDate = to,
        )
    }

    fun onClosingPeriodFilterSelected(period: PreviewPeriodFilter) {
        filterStateFlow.value = filterStateFlow.value.copy(
            closingPeriodFilter = period,
        )
    }

    fun onClosingCustomDatesChanged(from: String, to: String) {
        filterStateFlow.value = filterStateFlow.value.copy(
            closingPeriodFromDate = from,
            closingPeriodToDate = to,
        )
    }

    fun requestOpenCsvExportDialog() {
        val currentCsv = csvExportStateFlow.value
        if (currentCsv.isBusy || isCsvExportLock.get()) {
            return
        }
        val filter = filterStateFlow.value
        val zoneId = ZoneId.systemDefault()
        val filteredClosingSales = filterSalesByLocalPeriod(
            sales = latestRealSales,
            periodFilter = filter.closingPeriodFilter,
            fromDate = filter.closingPeriodFromDate,
            toDate = filter.closingPeriodToDate,
            zoneId = zoneId,
        )
        if (filteredClosingSales.isEmpty()) {
            csvExportStateFlow.value = currentCsv.copy(
                dialogVisible = false,
                feedbackMessage = "Nenhuma venda encontrada no período selecionado.",
                feedbackIsError = false,
                feedbackIsPartial = false,
            )
            return
        }
        csvExportStateFlow.value = currentCsv.copy(
            dialogVisible = true,
            feedbackMessage = null,
            feedbackIsError = false,
            feedbackIsPartial = false,
        )
    }

    fun selectCsvExportMode(mode: CsvExportMode) {
        val currentCsv = csvExportStateFlow.value
        if (currentCsv.isBusy) {
            return
        }
        csvExportStateFlow.value = currentCsv.copy(selectedMode = mode)
    }

    fun dismissCsvExportDialog() {
        val currentCsv = csvExportStateFlow.value
        if (currentCsv.isPreparing || currentCsv.isWriting) {
            return
        }
        csvExportStateFlow.value = currentCsv.copy(dialogVisible = false)
    }

    fun confirmCsvExport() {
        val currentCsv = csvExportStateFlow.value
        if (currentCsv.isWriting || currentCsv.pendingDocumentRequest != null) {
            return
        }
        if (!isCsvExportLock.compareAndSet(false, true)) {
            return
        }

        val selectedMode = currentCsv.selectedMode
        val capturedFilter = filterStateFlow.value
        val capturedSales = latestRealSales
        val zoneId = ZoneId.systemDefault()

        csvExportStateFlow.value = currentCsv.copy(
            dialogVisible = false,
            isPreparing = true,
            feedbackMessage = null,
            feedbackIsError = false,
            feedbackIsPartial = false,
        )

        viewModelScope.launch {
            try {
                val period = resolveClosingExportPeriod(capturedFilter, zoneId)
                val exportResult = if (period == null) {
                    null
                } else {
                    withContext(Dispatchers.Default) {
                        val closingSales = filterSalesByLocalPeriod(
                            sales = capturedSales,
                            periodFilter = capturedFilter.closingPeriodFilter,
                            fromDate = capturedFilter.closingPeriodFromDate,
                            toDate = capturedFilter.closingPeriodToDate,
                            zoneId = zoneId,
                        )
                        if (closingSales.isEmpty()) {
                            null
                        } else {
                            CsvSaleExporter.export(
                                sales = closingSales,
                                period = period,
                                zoneId = zoneId,
                                generatedOnDate = LocalDate.now(zoneId),
                            )
                        }
                    }
                }

                if (exportResult == null || exportResult.exportedSalesCount == 0) {
                    activeCsvExportSession = null
                    isCsvExportLock.set(false)
                    csvExportStateFlow.value = csvExportStateFlow.value.copy(
                        isPreparing = false,
                        pendingDocumentRequest = null,
                        feedbackMessage = "Nenhuma venda encontrada no período selecionado.",
                        feedbackIsError = false,
                        feedbackIsPartial = false,
                    )
                } else {
                    activeCsvExportSession = CsvExportSession(
                        mode = selectedMode,
                        result = exportResult,
                    )
                    val firstKind = if (selectedMode == CsvExportMode.DETAILED_ONLY) {
                        CsvDocumentKind.DETAILED
                    } else {
                        CsvDocumentKind.SUMMARY
                    }
                    val firstFileName = if (firstKind == CsvDocumentKind.SUMMARY) {
                        exportResult.suggestedSummaryFileName
                    } else {
                        exportResult.suggestedDetailedFileName
                    }
                    isCsvExportLock.set(false)
                    csvExportStateFlow.value = csvExportStateFlow.value.copy(
                        isPreparing = false,
                        pendingDocumentRequest = PendingCsvDocumentRequest(
                            requestToken = UUID.randomUUID().toString(),
                            kind = firstKind,
                            suggestedFileName = firstFileName,
                        ),
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (ex: CsvExportValidationException) {
                activeCsvExportSession = null
                isCsvExportLock.set(false)
                csvExportStateFlow.value = csvExportStateFlow.value.copy(
                    isPreparing = false,
                    pendingDocumentRequest = null,
                    feedbackMessage = ex.message ?: "Não foi possível validar os snapshots financeiros para CSV.",
                    feedbackIsError = true,
                    feedbackIsPartial = false,
                )
            } catch (ex: Throwable) {
                activeCsvExportSession = null
                isCsvExportLock.set(false)
                csvExportStateFlow.value = csvExportStateFlow.value.copy(
                    isPreparing = false,
                    pendingDocumentRequest = null,
                    feedbackMessage = ex.message?.takeIf { it.isNotBlank() }
                        ?: "Não foi possível preparar a exportação CSV.",
                    feedbackIsError = true,
                    feedbackIsPartial = false,
                )
            } finally {
                isCsvExportLock.set(false)
            }
        }
    }

    fun onCsvDocumentDestinationSelected(
        uri: Uri?,
        writeBytes: suspend (Uri, ByteArray) -> Unit,
    ) {
        if (!isCsvExportLock.compareAndSet(false, true)) {
            return
        }
        val currentCsv = csvExportStateFlow.value
        val pending = currentCsv.pendingDocumentRequest
        val session = activeCsvExportSession
        if (pending == null || session == null) {
            csvExportStateFlow.value = currentCsv.copy(
                isWriting = false,
                pendingDocumentRequest = null,
            )
            isCsvExportLock.set(false)
            return
        }

        if (uri == null) {
            activeCsvExportSession = null
            val wasSecondOfBoth = pending.kind == CsvDocumentKind.DETAILED &&
                session.mode == CsvExportMode.BOTH &&
                session.summarySaved
            csvExportStateFlow.value = currentCsv.copy(
                isWriting = false,
                pendingDocumentRequest = null,
                feedbackMessage = if (wasSecondOfBoth) {
                    "Exportação parcial: resumo das vendas (${session.result.suggestedSummaryFileName}) salvo, mas o detalhamento completo foi cancelado."
                } else {
                    "Exportação CSV cancelada. Nenhum arquivo foi salvo."
                },
                feedbackIsError = false,
                feedbackIsPartial = wasSecondOfBoth,
            )
            isCsvExportLock.set(false)
            return
        }

        val bytes = when (pending.kind) {
            CsvDocumentKind.SUMMARY -> session.result.summaryCsvBytes()
            CsvDocumentKind.DETAILED -> session.result.detailedCsvBytes()
        }

        csvExportStateFlow.value = currentCsv.copy(
            pendingDocumentRequest = null,
            isWriting = true,
            feedbackMessage = null,
            feedbackIsError = false,
            feedbackIsPartial = false,
        )

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    writeBytes(uri, bytes)
                }

                val updatedSession = when (pending.kind) {
                    CsvDocumentKind.SUMMARY -> session.copy(summarySaved = true)
                    CsvDocumentKind.DETAILED -> session.copy(detailedSaved = true)
                }

                if (updatedSession.mode == CsvExportMode.BOTH && !updatedSession.detailedSaved) {
                    activeCsvExportSession = updatedSession
                    isCsvExportLock.set(false)
                    csvExportStateFlow.value = csvExportStateFlow.value.copy(
                        isWriting = false,
                        pendingDocumentRequest = PendingCsvDocumentRequest(
                            requestToken = UUID.randomUUID().toString(),
                            kind = CsvDocumentKind.DETAILED,
                            suggestedFileName = updatedSession.result.suggestedDetailedFileName,
                        ),
                    )
                } else {
                    activeCsvExportSession = null
                    val detailText = when (updatedSession.mode) {
                        CsvExportMode.SUMMARY_ONLY ->
                            "Resumo das vendas salvo (${updatedSession.result.suggestedSummaryFileName})."
                        CsvExportMode.DETAILED_ONLY ->
                            "Detalhamento completo salvo (${updatedSession.result.suggestedDetailedFileName})."
                        CsvExportMode.BOTH ->
                            "Resumo das vendas (${updatedSession.result.suggestedSummaryFileName}) e detalhamento completo (${updatedSession.result.suggestedDetailedFileName}) salvos."
                    }
                    isCsvExportLock.set(false)
                    csvExportStateFlow.value = csvExportStateFlow.value.copy(
                        isWriting = false,
                        pendingDocumentRequest = null,
                        feedbackMessage = "CSV exportado com sucesso. $detailText",
                        feedbackIsError = false,
                        feedbackIsPartial = false,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                activeCsvExportSession = null
                val wasSecondOfBoth = pending.kind == CsvDocumentKind.DETAILED &&
                    session.mode == CsvExportMode.BOTH &&
                    session.summarySaved
                val targetLabel = if (pending.kind == CsvDocumentKind.SUMMARY) {
                    "Resumo das vendas"
                } else {
                    "Detalhamento completo"
                }
                isCsvExportLock.set(false)
                csvExportStateFlow.value = csvExportStateFlow.value.copy(
                    isWriting = false,
                    pendingDocumentRequest = null,
                    feedbackMessage = if (wasSecondOfBoth) {
                        "Exportação parcial: resumo das vendas (${session.result.suggestedSummaryFileName}) foi salvo, mas ocorreu erro ao gravar o detalhamento completo. Tente novamente."
                    } else {
                        "Não foi possível salvar o arquivo CSV ($targetLabel). Tente novamente."
                    },
                    feedbackIsError = true,
                    feedbackIsPartial = wasSecondOfBoth,
                )
            } finally {
                isCsvExportLock.set(false)
            }
        }
    }

    private fun resolveClosingExportPeriod(
        filter: FilterState,
        zoneId: ZoneId,
    ): CsvExportPeriod? {
        val today = LocalDate.now(zoneId)
        return when (filter.closingPeriodFilter) {
            PreviewPeriodFilter.TODAY -> CsvExportPeriod(today, today)
            PreviewPeriodFilter.WEEK -> CsvExportPeriod(today.minusDays(6), today)
            PreviewPeriodFilter.MONTH -> CsvExportPeriod(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
            PreviewPeriodFilter.ALL -> CsvExportPeriod(null, null)
            PreviewPeriodFilter.CUSTOM -> {
                val from = parseOptionalIsoDate(filter.closingPeriodFromDate)
                val to = parseOptionalIsoDate(filter.closingPeriodToDate)
                if (from != null && to != null && from.isAfter(to)) {
                    null
                } else {
                    CsvExportPeriod(from, to)
                }
            }
        }
    }

    fun onPeriodFilterSelected(period: PreviewPeriodFilter) {
        filterStateFlow.value = filterStateFlow.value.copy(
            periodFilter = period,
            selectedSalesDate = null,
        )
    }

    fun onCustomDatesChanged(from: String, to: String) {
        filterStateFlow.value = filterStateFlow.value.copy(
            periodFromDate = from,
            periodToDate = to,
            selectedSalesDate = null,
        )
    }

    fun toggleShowAllPeopleOnHome() {
        val current = filterStateFlow.value
        filterStateFlow.value = current.copy(showAllPeopleOnHome = !current.showAllPeopleOnHome)
    }

    fun onSalesSearchChanged(query: String) {
        filterStateFlow.value = filterStateFlow.value.copy(salesSearchQuery = query)
    }

    fun onSalesMethodFilterSelected(method: PreviewPaymentMethod?) {
        filterStateFlow.value = filterStateFlow.value.copy(salesMethodFilter = method)
    }

    fun openSalesForDay(date: LocalDate) {
        filterStateFlow.value = filterStateFlow.value.copy(
            selectedSalesDate = date,
            salesSearchQuery = "",
            salesMethodFilter = null,
        )
        if (!isDeletingLock.get()) {
            salesActionFlow.value = SalesActionState()
        }
    }

    fun clearSelectedSalesDate() {
        filterStateFlow.value = filterStateFlow.value.copy(
            selectedSalesDate = null,
        )
    }

    fun requestDeleteSale(saleId: String) {
        if (isDeletingLock.get() || salesActionFlow.value.deleteState.isDeleting) {
            return
        }
        val cached = latestRealSales.firstOrNull { it.id == saleId }
        if (cached != null) {
            salesActionFlow.value = SalesActionState(
                deleteState = SaleDeleteUiState(targetSale = cached),
                feedbackMessage = null,
                feedbackIsError = false,
            )
            return
        }
        viewModelScope.launch {
            val loaded = try {
                withContext(Dispatchers.IO) {
                    saleRepository.getSaleById(saleId)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
            if (loaded != null) {
                salesActionFlow.value = SalesActionState(
                    deleteState = SaleDeleteUiState(targetSale = loaded),
                    feedbackMessage = null,
                    feedbackIsError = false,
                )
            } else {
                salesActionFlow.value = SalesActionState(
                    deleteState = SaleDeleteUiState(),
                    feedbackMessage = "Esta venda já havia sido excluída ou não existe mais.",
                    feedbackIsError = true,
                )
            }
        }
    }

    fun dismissDeleteSaleDialog() {
        if (isDeletingLock.get() || salesActionFlow.value.deleteState.isDeleting) {
            return
        }
        salesActionFlow.value = salesActionFlow.value.copy(
            deleteState = SaleDeleteUiState(),
        )
    }

    fun confirmDeleteSale() {
        if (!isDeletingLock.compareAndSet(false, true)) {
            return
        }
        val currentAction = salesActionFlow.value
        val target = currentAction.deleteState.targetSale
        if (target == null) {
            isDeletingLock.set(false)
            return
        }
        salesActionFlow.value = currentAction.copy(
            deleteState = currentAction.deleteState.copy(
                isDeleting = true,
                errorMessage = null,
            ),
            feedbackMessage = null,
            feedbackIsError = false,
        )

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    saleRepository.deleteSale(target.id)
                }
                salesActionFlow.value = SalesActionState(
                    deleteState = SaleDeleteUiState(),
                    feedbackMessage = "Venda excluída com sucesso.",
                    feedbackIsError = false,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (ex: SalePersistenceException) {
                val msg = ex.message.orEmpty()
                if (msg.contains("não encontrada", ignoreCase = true)) {
                    salesActionFlow.value = SalesActionState(
                        deleteState = SaleDeleteUiState(),
                        feedbackMessage = "Esta venda já havia sido excluída ou não existe mais.",
                        feedbackIsError = true,
                    )
                } else {
                    salesActionFlow.value = salesActionFlow.value.copy(
                        deleteState = salesActionFlow.value.deleteState.copy(
                            isDeleting = false,
                            errorMessage = msg.ifBlank {
                                "Não foi possível excluir a venda. Tente novamente."
                            },
                        ),
                    )
                }
            } catch (ex: Throwable) {
                salesActionFlow.value = salesActionFlow.value.copy(
                    deleteState = salesActionFlow.value.deleteState.copy(
                        isDeleting = false,
                        errorMessage = ex.message?.takeIf { it.isNotBlank() }
                            ?: "Não foi possível excluir a venda. Tente novamente.",
                    ),
                )
            } finally {
                isDeletingLock.set(false)
            }
        }
    }

    /**
     * Carrega uma venda real completa do [SaleRepository] e entra em modo edição na tela Vender,
     * preservando os snapshots históricos originais de produtos, preços unitários e maquininha.
     */
    fun startEditingSale(
        saleId: String,
        onLoaded: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val loadedSale = try {
                withContext(Dispatchers.IO) {
                    saleRepository.getSaleById(saleId)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            } ?: return@launch

            val calc = loadedSale.calculation
            val historicalProducts = calc.items.associate { item ->
                item.productId to item.toSnapshotProduct(
                    createdAtEpochMs = loadedSale.soldAtEpochMs,
                    updatedAtEpochMs = loadedSale.updatedAtEpochMs,
                )
            }
            val quantities = calc.items.associate { it.productId to it.quantity }
            val discountTarget = when (val cfg = calc.discountConfig) {
                is SaleDiscountConfigSnapshot.Proportional -> VenderFormUiState.DISCOUNT_PROPORTIONAL
                is SaleDiscountConfigSnapshot.SpecificProduct -> cfg.productId
            }
            val hasDiscount = calc.finalAmount.rawCents < calc.originalSubtotal.rawCents
            val snap = calc.machineSnapshot

            val resolvedEditBrandKey = snap?.resolvedAppliedBrandKey ?: CardBrand.VISA.storageKey
            val resolvedEditBrand = CardBrand.fromStorageKey(resolvedEditBrandKey) ?: CardBrand.VISA

            val initialEditForm = VenderFormUiState(
                editingSaleId = loadedSale.id,
                originalEditingSale = loadedSale,
                historicalProductsById = historicalProducts,
                historicalMachineSnapshot = snap,
                preferHistoricalMachineSnapshot = snap != null,
                selectedQuantities = quantities,
                pickerOpen = false,
                pickerSearchQuery = "",
                finalAmountInput = CurrencyFormatter.formatCentsForInput(calc.finalAmount.rawCents),
                finalAmountTouched = hasDiscount,
                discountTargetProductId = discountTarget,
                selectedMethod = calc.paymentMethod.toPreviewPaymentMethod(),
                selectedMachineId = snap?.machineId,
                machinePickerOpen = false,
                machinePickerSearchQuery = "",
                selectedBrand = resolvedEditBrand,
                selectedInstallments = snap?.appliedInstallments ?: 1,
                feeOverrideEnabled = snap?.manualRateOverrideBps != null,
                manualRateInput = snap?.manualRateOverrideBps?.let { RateFormatter.formatBpsForInput(it) }.orEmpty(),
                note = calc.note,
                recalculatedNotice = null,
                recalculatedNoticeIsError = false,
                showDiscardEditDialog = false,
                isSaving = false,
                feedbackMessage = null,
                feedbackIsError = false,
                selectedBrandKey = resolvedEditBrandKey,
            )

            venderFormFlow.value = syncForm(
                form = initialEditForm,
                activeProducts = latestActiveProducts,
                machines = latestMachines,
            )
            onLoaded()
        }
    }

    fun hasUnsavedEditChanges(): Boolean {
        val form = venderFormFlow.value
        val original = form.originalEditingSale ?: return false
        if (!isNoteOnlyOrNoFinancialChange(form, original)) return true
        return form.note.trim() != original.calculation.note.trim()
    }

    fun handleEditBackRequested(onNavigateBackToSales: () -> Unit) {
        if (!venderFormFlow.value.isEditing) {
            onNavigateBackToSales()
            return
        }
        if (hasUnsavedEditChanges()) {
            venderFormFlow.value = venderFormFlow.value.copy(showDiscardEditDialog = true)
        } else {
            cancelEditing()
            onNavigateBackToSales()
        }
    }

    fun dismissDiscardEditDialog() {
        venderFormFlow.value = venderFormFlow.value.copy(showDiscardEditDialog = false)
    }

    fun confirmDiscardEditing(onDiscarded: () -> Unit) {
        cancelEditing()
        onDiscarded()
    }

    fun cancelEditing() {
        isSavingLock.set(false)
        venderFormFlow.value = VenderFormUiState(
            selectedMethod = PreviewPaymentMethod.PIX,
        )
    }

    fun setProductPickerOpen(open: Boolean) {
        venderFormFlow.value = venderFormFlow.value.copy(
            pickerOpen = open,
            pickerSearchQuery = if (!open) "" else venderFormFlow.value.pickerSearchQuery,
        )
    }

    fun onProductPickerSearchChanged(query: String) {
        venderFormFlow.value = venderFormFlow.value.copy(pickerSearchQuery = query)
    }

    fun toggleProductSelection(productId: String) {
        val current = venderFormFlow.value
        val updated = current.selectedQuantities.toMutableMap()
        if (updated.containsKey(productId)) {
            updated.remove(productId)
        } else {
            updated[productId] = 1
        }
        venderFormFlow.value = applyQuantityUpdate(current, updated)
    }

    fun incrementProductQuantity(productId: String) {
        val current = venderFormFlow.value
        val updated = current.selectedQuantities.toMutableMap()
        val currentQty = updated[productId] ?: 0
        updated[productId] = (currentQty + 1).coerceAtMost(SaleFinancialEngine.MAX_ITEM_QUANTITY)
        venderFormFlow.value = applyQuantityUpdate(current, updated)
    }

    fun decrementProductQuantity(productId: String) {
        val current = venderFormFlow.value
        val updated = current.selectedQuantities.toMutableMap()
        val qty = updated[productId] ?: 0
        if (qty <= 1) {
            updated.remove(productId)
        } else {
            updated[productId] = qty - 1
        }
        venderFormFlow.value = applyQuantityUpdate(current, updated)
    }

    fun removeProductFromSale(productId: String) {
        val current = venderFormFlow.value
        val updated = current.selectedQuantities.toMutableMap()
        updated.remove(productId)
        venderFormFlow.value = applyQuantityUpdate(current, updated)
    }

    fun onFinalAmountInputChanged(value: String) {
        val current = venderFormFlow.value
        val targetMissingNow = current.discountTargetProductId != VenderFormUiState.DISCOUNT_PROPORTIONAL &&
            !current.selectedQuantities.containsKey(current.discountTargetProductId)
        venderFormFlow.value = current.copy(
            finalAmountInput = value,
            finalAmountTouched = value.isNotBlank(),
            recalculatedNotice = if (targetMissingNow) current.recalculatedNotice else null,
            recalculatedNoticeIsError = if (targetMissingNow) current.recalculatedNoticeIsError else false,
            feedbackMessage = null,
            feedbackIsError = false,
        )
    }

    fun onDiscountTargetSelected(targetProductId: String) {
        venderFormFlow.value = venderFormFlow.value.copy(
            discountTargetProductId = targetProductId,
            recalculatedNotice = null,
            recalculatedNoticeIsError = false,
            feedbackMessage = null,
            feedbackIsError = false,
        )
    }

    fun onPaymentMethodSelected(method: PreviewPaymentMethod) {
        val current = venderFormFlow.value
        val matchesOriginalMethod = current.isEditing &&
            current.originalEditingSale?.calculation?.paymentMethod == method.toSalePaymentMethod()
        val keepHistorical = current.preferHistoricalMachineSnapshot && matchesOriginalMethod
        venderFormFlow.value = syncForm(
            form = current.copy(
                selectedMethod = method,
                preferHistoricalMachineSnapshot = keepHistorical,
                machinePickerOpen = false,
                machinePickerSearchQuery = "",
                feedbackMessage = null,
                feedbackIsError = false,
            ),
            activeProducts = latestActiveProducts,
            machines = latestMachines,
        )
    }

    fun setMachinePickerOpen(open: Boolean) {
        venderFormFlow.value = venderFormFlow.value.copy(
            machinePickerOpen = open,
            machinePickerSearchQuery = if (!open) "" else venderFormFlow.value.machinePickerSearchQuery,
        )
    }

    fun onMachinePickerSearchChanged(query: String) {
        venderFormFlow.value = venderFormFlow.value.copy(machinePickerSearchQuery = query)
    }

    fun onMachineSelected(machineId: String) {
        val current = venderFormFlow.value
        val catalogMatch = latestMachines.firstOrNull { it.id == machineId }
        val keepingMissingHistoricalSnapshot = current.isEditing &&
            current.historicalMachineSnapshot?.machineId == machineId &&
            (catalogMatch == null || (current.selectedMethod == PreviewPaymentMethod.CREDIT && !catalogMatch.creditEnabled))

        venderFormFlow.value = syncForm(
            form = current.copy(
                selectedMachineId = machineId,
                preferHistoricalMachineSnapshot = keepingMissingHistoricalSnapshot,
                machinePickerOpen = false,
                machinePickerSearchQuery = "",
                feedbackMessage = null,
                feedbackIsError = false,
            ),
            activeProducts = latestActiveProducts,
            machines = latestMachines,
        )
    }

    fun onBrandSelected(brand: CardBrand) {
        onBrandKeySelected(brand.storageKey)
    }

    fun onBrandKeySelected(brandKey: String) {
        val current = venderFormFlow.value
        val resolvedStd = CardBrand.fromStorageKey(brandKey) ?: CardBrand.VISA
        val stillMatchesSnapshot = current.preferHistoricalMachineSnapshot &&
            current.historicalMachineSnapshot?.resolvedAppliedBrandKey == brandKey
        venderFormFlow.value = current.copy(
            selectedBrand = resolvedStd,
            selectedBrandKey = brandKey,
            preferHistoricalMachineSnapshot = stillMatchesSnapshot,
            feedbackMessage = null,
            feedbackIsError = false,
        )
    }

    fun onInstallmentsSelected(installments: Int) {
        val current = venderFormFlow.value
        val stillMatchesSnapshot = current.preferHistoricalMachineSnapshot &&
            (current.historicalMachineSnapshot?.appliedInstallments ?: 1) == installments
        val targetMachine = if (stillMatchesSnapshot) {
            resolvedSelectedMachine(current, latestMachines)
        } else {
            val eligible = when (current.selectedMethod) {
                PreviewPaymentMethod.DEBIT -> latestMachines
                PreviewPaymentMethod.CREDIT -> latestMachines.filter { it.creditEnabled }
                else -> emptyList()
            }
            eligible.firstOrNull { it.id == current.selectedMachineId }
                ?: resolvedSelectedMachine(current, latestMachines)
        }
        if (!stillMatchesSnapshot && targetMachine != null && !targetMachine.isInstallmentAllowed(installments)) {
            venderFormFlow.value = current.copy(
                feedbackMessage = "A maquininha \"${targetMachine.name}\" não permite parcelamento em ${installments}x.",
                feedbackIsError = true,
            )
            return
        }
        venderFormFlow.value = current.copy(
            selectedInstallments = installments,
            preferHistoricalMachineSnapshot = stillMatchesSnapshot,
            feedbackMessage = null,
            feedbackIsError = false,
        )
    }

    fun onFeeOverrideToggled(enabled: Boolean) {
        venderFormFlow.value = venderFormFlow.value.copy(
            feeOverrideEnabled = enabled,
            feedbackMessage = null,
            feedbackIsError = false,
        )
    }

    fun onManualRateInputChanged(rate: String) {
        venderFormFlow.value = venderFormFlow.value.copy(
            manualRateInput = rate,
            feedbackMessage = null,
            feedbackIsError = false,
        )
    }

    fun onNoteChanged(note: String) {
        venderFormFlow.value = venderFormFlow.value.copy(
            note = note,
            feedbackMessage = null,
            feedbackIsError = false,
        )
    }

    /**
     * Valida a seleção atual, calcula integralmente via [SaleFinancialEngine] (ou preserva
     * exatamente os snapshots quando somente a observação mudou na edição), confere todas as
     * igualdades matemáticas obrigatórias e persiste de forma atômica no [SaleRepository].
     * Protegido contra múltiplos cliques simultâneos com trava atômica.
     */
    fun saveSale() {
        if (!isSavingLock.compareAndSet(false, true)) {
            return
        }
        val currentForm = venderFormFlow.value
        venderFormFlow.value = currentForm.copy(
            isSaving = true,
            feedbackMessage = null,
            feedbackIsError = false,
        )

        viewModelScope.launch {
            try {
                val editingId = currentForm.editingSaleId
                val originalSale = currentForm.originalEditingSale
                val persisted = withContext(Dispatchers.Default) {
                    val activeSnapshot = latestActiveProducts
                    val machinesSnapshot = latestMachines

                    val calculationResult = if (
                        editingId != null &&
                        originalSale != null &&
                        isNoteOnlyOrNoFinancialChange(currentForm, originalSale)
                    ) {
                        // Edição apenas de observação (ou sem alterações financeiras): preserva 100% os snapshots anteriores
                        originalSale.calculation.copy(note = currentForm.note.trim())
                    } else {
                        val input = buildCalculationInputFromForm(
                            form = currentForm,
                            activeProducts = activeSnapshot,
                            machines = machinesSnapshot,
                        )
                        SaleFinancialEngine.calculate(input)
                    }

                    verifyPreSaveInvariants(calculationResult)
                    withContext(Dispatchers.IO) {
                        if (editingId != null) {
                            saleRepository.updateSale(editingId, calculationResult)
                        } else {
                            saleRepository.saveSale(calculationResult)
                        }
                    }
                }

                // Sucesso: sai do modo edição, limpa o formulário e exibe confirmação explícita
                val successMsg = if (editingId != null) {
                    "Alterações salvas com sucesso (${CurrencyFormatter.formatBrl(persisted.calculation.finalAmount)})."
                } else {
                    "Venda registrada com sucesso (${CurrencyFormatter.formatBrl(persisted.calculation.finalAmount)})."
                }
                venderFormFlow.value = VenderFormUiState(
                    selectedMethod = PreviewPaymentMethod.PIX,
                    isSaving = false,
                    feedbackMessage = successMsg,
                    feedbackIsError = false,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (ex: Throwable) {
                // Falha: preserva o modo edição e todos os campos digitados permitindo nova tentativa
                val fallback = if (currentForm.isEditing) {
                    "Não foi possível salvar as alterações. Verifique os dados e tente novamente."
                } else {
                    "Não foi possível registrar a venda. Verifique os dados e tente novamente."
                }
                venderFormFlow.value = venderFormFlow.value.copy(
                    isSaving = false,
                    feedbackMessage = ex.message ?: fallback,
                    feedbackIsError = true,
                )
            } finally {
                isSavingLock.set(false)
            }
        }
    }

    private fun applyQuantityUpdate(
        current: VenderFormUiState,
        updatedQuantities: Map<String, Int>,
    ): VenderFormUiState {
        val cleanQuantities = updatedQuantities.filterValues { it > 0 }
        val resolvedProducts = resolvedProductsMap(current, latestActiveProducts)

        val prevSubtotalCents = computeSelectedSubtotalCents(current.selectedQuantities, resolvedProducts)
        val prevParsedFinal = CurrencyFormatter.parseBrlToCents(current.finalAmountInput)
        val prevHadDiscount = current.finalAmountTouched &&
            prevParsedFinal != null &&
            prevSubtotalCents > 0L &&
            prevParsedFinal in 1L until prevSubtotalCents
        val prevDiscountCents = if (prevHadDiscount) {
            prevSubtotalCents - prevParsedFinal!!
        } else {
            0L
        }

        val newSubtotalCents = computeSelectedSubtotalCents(cleanQuantities, resolvedProducts)

        var nextFinalInput: String
        var nextFinalTouched: Boolean
        var notice: String? = null
        var noticeIsError = false

        if (cleanQuantities.isEmpty() || newSubtotalCents <= 0L) {
            nextFinalInput = ""
            nextFinalTouched = false
        } else if (prevDiscountCents > 0L) {
            val candidateFinal = newSubtotalCents - prevDiscountCents
            if (candidateFinal > 0L) {
                nextFinalInput = CurrencyFormatter.formatCentsForInput(candidateFinal)
                nextFinalTouched = true
                if (current.isEditing || newSubtotalCents != prevSubtotalCents) {
                    notice = "Subtotal recalculado para ${CurrencyFormatter.formatBrl(MoneyCents(newSubtotalCents))} " +
                        "com desconto preservado (${CurrencyFormatter.formatBrl(MoneyCents(prevDiscountCents))})."
                }
            } else {
                nextFinalInput = CurrencyFormatter.formatCentsOrZeroForInput(candidateFinal)
                nextFinalTouched = true
                notice = "Atenção: o desconto anterior (${CurrencyFormatter.formatBrl(MoneyCents(prevDiscountCents))}) " +
                    "ficou maior ou igual ao novo subtotal (${CurrencyFormatter.formatBrl(MoneyCents(newSubtotalCents))}). " +
                    "Ajuste o valor final antes de salvar."
                noticeIsError = true
            }
        } else if (!current.finalAmountTouched || prevParsedFinal == prevSubtotalCents) {
            nextFinalInput = CurrencyFormatter.formatCentsForInput(newSubtotalCents)
            nextFinalTouched = false
            if (current.isEditing && newSubtotalCents != prevSubtotalCents) {
                notice = "Valores recalculados para o novo subtotal (${CurrencyFormatter.formatBrl(MoneyCents(newSubtotalCents))})."
            }
        } else {
            nextFinalInput = current.finalAmountInput
            nextFinalTouched = current.finalAmountTouched
        }

        val specificTargetMissing = current.discountTargetProductId != VenderFormUiState.DISCOUNT_PROPORTIONAL &&
            !cleanQuantities.containsKey(current.discountTargetProductId)

        val nextDiscountTarget = if (cleanQuantities.isEmpty()) {
            VenderFormUiState.DISCOUNT_PROPORTIONAL
        } else if (specificTargetMissing && prevDiscountCents == 0L && (!current.finalAmountTouched || prevParsedFinal == prevSubtotalCents)) {
            VenderFormUiState.DISCOUNT_PROPORTIONAL
        } else {
            current.discountTargetProductId
        }

        if (cleanQuantities.isNotEmpty() && specificTargetMissing && nextDiscountTarget != VenderFormUiState.DISCOUNT_PROPORTIONAL) {
            val missingName = resolvedProducts[nextDiscountTarget]?.name ?: "produto removido"
            val targetMsg = "O produto \"$missingName\" que recebia o desconto específico foi removido. " +
                "Escolha outro produto ou selecione desconto proporcional."
            notice = if (notice != null && noticeIsError) "$notice $targetMsg" else targetMsg
            noticeIsError = true
        }

        return syncForm(
            form = current.copy(
                selectedQuantities = cleanQuantities,
                finalAmountInput = nextFinalInput,
                finalAmountTouched = nextFinalTouched,
                discountTargetProductId = nextDiscountTarget,
                recalculatedNotice = notice,
                recalculatedNoticeIsError = noticeIsError,
                feedbackMessage = null,
                feedbackIsError = false,
            ),
            activeProducts = latestActiveProducts,
            machines = latestMachines,
        )
    }

    private fun isNoteOnlyOrNoFinancialChange(
        form: VenderFormUiState,
        original: PersistedSale,
    ): Boolean {
        val calc = original.calculation
        val originalQuantities = calc.items.associate { it.productId to it.quantity }
        if (form.selectedQuantities.filterValues { it > 0 } != originalQuantities) return false

        val parsedFinal = if (form.finalAmountInput.isBlank()) {
            calc.originalSubtotal.rawCents
        } else {
            CurrencyFormatter.parseBrlToCents(form.finalAmountInput) ?: return false
        }
        if (parsedFinal != calc.finalAmount.rawCents) return false

        val hasDiscount = parsedFinal < calc.originalSubtotal.rawCents
        if (hasDiscount) {
            when (val cfg = calc.discountConfig) {
                is SaleDiscountConfigSnapshot.Proportional -> {
                    if (form.discountTargetProductId != VenderFormUiState.DISCOUNT_PROPORTIONAL) return false
                }
                is SaleDiscountConfigSnapshot.SpecificProduct -> {
                    if (form.discountTargetProductId != cfg.productId) return false
                }
            }
        }

        if (form.selectedMethod.toSalePaymentMethod() != calc.paymentMethod) return false

        val origSnap = calc.machineSnapshot
        if (calc.paymentMethod.usesMachine) {
            if (origSnap == null) return false
            if (!canUseHistoricalMachineSnapshot(form)) return false
            if (form.selectedMachineId != origSnap.machineId) return false
            val expectedBrandKey = origSnap.resolvedAppliedBrandKey ?: CardBrand.VISA.storageKey
            if (origSnap.brandRatesEnabled && calc.paymentMethod.usesMachine && form.resolvedBrandKey() != expectedBrandKey) {
                return false
            }
            val expectedInstallments = origSnap.appliedInstallments ?: 1
            if (origSnap.installmentsEnabled && calc.paymentMethod == SalePaymentMethod.CREDIT && form.selectedInstallments != expectedInstallments) {
                return false
            }
            val wasManual = origSnap.manualRateOverrideBps != null
            if (form.feeOverrideEnabled != wasManual) return false
            if (wasManual) {
                val parsedManual = RateFormatter.parsePercentageToBps(form.manualRateInput) ?: return false
                if (parsedManual != origSnap.manualRateOverrideBps) return false
            }
        }
        return true
    }

    private fun buildCalculationInputFromForm(
        form: VenderFormUiState,
        activeProducts: List<Product>,
        machines: List<Machine>,
    ): SaleCalculationInput {
        val productMap = resolvedProductsMap(form, activeProducts)
        val itemInputs = form.selectedQuantities.entries
            .filter { it.value > 0 }
            .map { (productId, qty) ->
                val product = productMap[productId]
                    ?: throw SaleCalculationException("Um produto selecionado não está mais ativo no cadastro. Revise os itens.")
                SaleItemInput(
                    product = product,
                    quantity = qty,
                )
            }
        if (itemInputs.isEmpty()) {
            throw SaleCalculationException("Selecione pelo menos um produto antes de salvar a venda.")
        }

        var originalSubtotalCents = 0L
        itemInputs.forEach { line ->
            val lineTotal = MonetaryRounding.safeMultiply(line.product.price.rawCents, line.quantity)
            originalSubtotalCents = MonetaryRounding.safeAdd(originalSubtotalCents, lineTotal)
        }

        val finalAmountOverride: MoneyCents? = if (form.finalAmountInput.isBlank()) {
            null
        } else {
            val parsedCents = CurrencyFormatter.parseBrlToCents(form.finalAmountInput)
                ?: throw SaleCalculationException("Valor final da venda inválido. Informe um valor em reais (ex.: 95,00).")
            if (parsedCents <= 0L) {
                throw SaleCalculationException("O valor final da venda deve ser maior que zero.")
            }
            if (parsedCents > originalSubtotalCents) {
                throw SaleCalculationException(
                    "O valor final (${CurrencyFormatter.formatBrl(MoneyCents(parsedCents))}) não pode ultrapassar o subtotal original (${CurrencyFormatter.formatBrl(MoneyCents(originalSubtotalCents))}).",
                )
            }
            MoneyCents(parsedCents)
        }

        val effectiveFinalCents = finalAmountOverride?.rawCents ?: originalSubtotalCents
        val hasDiscount = effectiveFinalCents < originalSubtotalCents
        val discountMode = if (
            hasDiscount &&
            form.discountTargetProductId != VenderFormUiState.DISCOUNT_PROPORTIONAL
        ) {
            val targetId = form.discountTargetProductId
            if (itemInputs.none { it.product.id == targetId }) {
                throw SaleCalculationException(
                    "O produto que recebia o desconto específico foi removido. Escolha outro produto ou selecione desconto proporcional antes de salvar.",
                )
            }
            SaleDiscountInput.SpecificProduct(targetId)
        } else {
            SaleDiscountInput.Proportional
        }

        val paymentMethod = form.selectedMethod.toSalePaymentMethod()
        val selectedMachine = if (paymentMethod.usesMachine) {
            val machineId = form.selectedMachineId
                ?: throw SaleCalculationException(
                    "Selecione uma maquininha compatível para pagamentos em ${paymentMethod.label}.",
                )
            if (canUseHistoricalMachineSnapshot(form)) {
                val orig = form.originalEditingSale
                form.historicalMachineSnapshot!!.toSnapshotMachine(
                    paymentMethod = paymentMethod,
                    soldAtEpochMs = orig?.soldAtEpochMs ?: 0L,
                    updatedAtEpochMs = orig?.updatedAtEpochMs ?: 0L,
                )
            } else {
                val machine = machines.firstOrNull { it.id == machineId }
                    ?: throw SaleCalculationException("A maquininha selecionada não foi encontrada no cadastro.")
                if (paymentMethod == SalePaymentMethod.CREDIT && !machine.creditEnabled) {
                    throw SaleCalculationException("A maquininha \"${machine.name}\" não possui crédito ativado.")
                }
                machine
            }
        } else {
            null
        }

        val manualRateOverrideBps = if (paymentMethod.usesMachine && form.feeOverrideEnabled) {
            if (form.manualRateInput.isBlank()) {
                throw SaleCalculationException("Informe a tarifa manual (%) ou desmarque a substituição de tarifa.")
            }
            val parsedBps = RateFormatter.parsePercentageToBps(form.manualRateInput)
                ?: throw SaleCalculationException("Tarifa manual inválida. Use até 2 casas decimais (ex.: 3,50).")
            if (parsedBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                throw SaleCalculationException("A tarifa manual deve estar entre 0,00% e 100,00%.")
            }
            parsedBps
        } else {
            null
        }

        val cardBrandKey = if (paymentMethod.usesMachine && selectedMachine?.brandRatesEnabled == true) {
            if (
                paymentMethod == SalePaymentMethod.DEBIT &&
                canUseHistoricalMachineSnapshot(form) &&
                form.historicalMachineSnapshot?.resolvedAppliedBrandKey == null &&
                form.resolvedBrandKey() == CardBrand.VISA.storageKey
            ) {
                null
            } else {
                form.resolvedBrandKey()
            }
        } else {
            null
        }
        val cardBrand = cardBrandKey?.let { CardBrand.fromStorageKey(it) }
        val installments = if (paymentMethod == SalePaymentMethod.CREDIT) {
            if (selectedMachine?.installmentsEnabled == true) {
                if (!canUseHistoricalMachineSnapshot(form) && !selectedMachine.isInstallmentAllowed(form.selectedInstallments)) {
                    throw SaleCalculationException(
                        "A maquininha \"${selectedMachine.name}\" não permite parcelamento em ${form.selectedInstallments}x.",
                    )
                }
                form.selectedInstallments
            } else {
                1
            }
        } else {
            null
        }

        return SaleCalculationInput(
            items = itemInputs,
            finalAmountOverride = finalAmountOverride,
            discountMode = discountMode,
            paymentMethod = paymentMethod,
            machine = selectedMachine,
            cardBrand = cardBrand,
            installments = installments,
            manualRateOverrideBps = manualRateOverrideBps,
            note = form.note,
            cardBrandKey = cardBrandKey,
        )
    }

    private fun verifyPreSaveInvariants(result: SaleCalculationResult) {
        if (result.originalSubtotal.rawCents - result.discountTotal.rawCents != result.finalAmount.rawCents) {
            throw SalePersistenceException("Conferência falhou: subtotal original menos desconto difere do valor final.")
        }
        if (result.items.sumOf { it.finalAmount.rawCents } != result.finalAmount.rawCents) {
            throw SalePersistenceException("Conferência falhou: soma dos valores finais dos itens difere do valor final.")
        }
        if (result.items.sumOf { it.feeAmount.rawCents } != result.feeTotal.rawCents) {
            throw SalePersistenceException("Conferência falhou: soma das tarifas individuais difere da tarifa total.")
        }
        if (result.personTotals.sumOf { it.netAmount.rawCents } != result.netTotal.rawCents) {
            throw SalePersistenceException("Conferência falhou: soma dos líquidos das pessoas difere do líquido total.")
        }
        if (result.finalAmount.rawCents - result.feeTotal.rawCents != result.netTotal.rawCents) {
            throw SalePersistenceException("Conferência falhou: valor final menos tarifa total difere do líquido total.")
        }
        SalePersistenceValidator.validateForPersistence(result)
    }

    private fun syncForm(
        form: VenderFormUiState,
        activeProducts: List<Product>,
        machines: List<Machine>,
    ): VenderFormUiState {
        val resolvedMap = resolvedProductsMap(form, activeProducts)
        val filteredQuantities = form.selectedQuantities.filterKeys { resolvedMap.containsKey(it) }
        val validTarget = if (filteredQuantities.isEmpty()) {
            VenderFormUiState.DISCOUNT_PROPORTIONAL
        } else if (
            !form.isEditing &&
            !form.finalAmountTouched &&
            form.discountTargetProductId != VenderFormUiState.DISCOUNT_PROPORTIONAL &&
            !filteredQuantities.containsKey(form.discountTargetProductId)
        ) {
            VenderFormUiState.DISCOUNT_PROPORTIONAL
        } else {
            form.discountTargetProductId
        }
        val updatedSubtotalCents = computeSelectedSubtotalCents(filteredQuantities, resolvedMap)
        val nextFinalInput = if (!form.finalAmountTouched) {
            CurrencyFormatter.formatCentsForInput(updatedSubtotalCents)
        } else {
            form.finalAmountInput
        }

        val usesMachine = form.selectedMethod == PreviewPaymentMethod.DEBIT ||
            form.selectedMethod == PreviewPaymentMethod.CREDIT
        val validMachine = resolvedSelectedMachine(form, machines)
        val nextInstallments = if (
            form.selectedMethod == PreviewPaymentMethod.CREDIT &&
            validMachine != null &&
            validMachine.installmentsEnabled
        ) {
            if (canUseHistoricalMachineSnapshot(form)) {
                form.selectedInstallments.takeIf { it in InstallmentTiers.SUPPORTED } ?: 1
            } else {
                validMachine.coerceInstallments(form.selectedInstallments)
            }
        } else {
            1
        }

        val currentBrandKey = form.resolvedBrandKey()
        val isStandardBrand = CardBrand.fromStorageKey(currentBrandKey) != null
        val validCustomBrandOnMachine = usesMachine &&
            validMachine != null &&
            (canUseHistoricalMachineSnapshot(form) ||
                (validMachine.brandRatesEnabled && validMachine.findCustomBrand(currentBrandKey) != null))
        val nextBrandKey = if (isStandardBrand || validCustomBrandOnMachine) {
            currentBrandKey
        } else {
            CardBrand.VISA.storageKey
        }
        val nextBrand = CardBrand.fromStorageKey(nextBrandKey) ?: CardBrand.VISA

        return form.copy(
            selectedQuantities = filteredQuantities,
            discountTargetProductId = validTarget,
            finalAmountInput = nextFinalInput,
            selectedMachineId = if (usesMachine) validMachine?.id else null,
            machinePickerOpen = if (usesMachine) form.machinePickerOpen else false,
            machinePickerSearchQuery = if (usesMachine) form.machinePickerSearchQuery else "",
            selectedBrand = nextBrand,
            selectedBrandKey = nextBrandKey,
            selectedInstallments = nextInstallments,
        )
    }

    private fun buildUiState(
        roomPrefCount: Int,
        registeredPeople: List<Person>,
        activeCatalogProducts: List<Product>,
        catalogProductCategories: List<ProductCategory> = emptyList(),
        machines: List<Machine>,
        realSales: List<PersistedSale>,
        realDailyReport: RealDailySalesReport,
        filter: FilterState,
        venderForm: VenderFormUiState,
        salesAction: SalesActionState,
        closingCsvExport: ClosingCsvExportUiState,
    ): MixVendasShellUiState {
        // 1. Dados demonstrativos preservados apenas para Fechamento (Etapa 4B-2)
        val sourceSales = if (filter.previewEnabled) basePreviewSales else emptyList()
        val filteredPreview = filterPreviewSales(sourceSales, filter)

        var previewGrossCents = 0L
        var previewFeeCents = 0L
        var previewNetCents = 0L
        filteredPreview.forEach { sale ->
            previewGrossCents += sale.gross.rawCents
            previewFeeCents += sale.fee.rawCents
            previewNetCents += sale.net.rawCents
        }

        val previewPersonTotals = PreviewFixtures.people.map { person ->
            var pGross = 0L
            var pFee = 0L
            var pNet = 0L
            filteredPreview.forEach { sale ->
                sale.allocations.filter { it.personId == person.id }.forEach { alloc ->
                    pGross += alloc.gross.rawCents
                    pFee += alloc.fee.rawCents
                    pNet += alloc.net.rawCents
                }
            }
            PreviewPersonSummary(
                person = person,
                gross = MoneyCents(pGross),
                fee = MoneyCents(pFee),
                net = MoneyCents(pNet),
            )
        }

        val paymentBreakdowns = PreviewPaymentMethod.entries.map { method ->
            val subset = filteredPreview.filter { it.method == method }
            PaymentMethodBreakdown(
                method = method,
                count = subset.size,
                gross = MoneyCents(subset.sumOf { it.gross.rawCents }),
                fee = MoneyCents(subset.sumOf { it.fee.rawCents }),
                net = MoneyCents(subset.sumOf { it.net.rawCents }),
            )
        }

        val machineBreakdowns = filteredPreview
            .filter { it.method == PreviewPaymentMethod.DEBIT || it.method == PreviewPaymentMethod.CREDIT }
            .groupBy { it.machineName ?: "Sem maquininha" }
            .map { (name, subset) ->
                MachineBreakdown(
                    machineName = name,
                    count = subset.size,
                    gross = MoneyCents(subset.sumOf { it.gross.rawCents }),
                    fee = MoneyCents(subset.sumOf { it.fee.rawCents }),
                    net = MoneyCents(subset.sumOf { it.net.rawCents }),
                )
            }

        val dayGroups = sourceSales
            .groupBy { it.date }
            .entries
            .sortedByDescending { it.key }
            .map { (date, list) ->
                DaySalesPreviewGroup(
                    date = date,
                    count = list.size,
                    gross = MoneyCents(list.sumOf { it.gross.rawCents }),
                )
            }

        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", ptBrLocale)
        val monthGroups = dayGroups
            .groupBy { YearMonth.from(it.date) }
            .entries
            .sortedByDescending { it.key }
            .map { (ym, days) ->
                val rawTitle = ym.atDay(1).format(monthFormatter)
                MonthSalesPreviewGroup(
                    yearMonth = ym,
                    label = rawTitle.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(ptBrLocale) else ch.toString()
                    },
                    days = days,
                    gross = MoneyCents(days.sumOf { it.gross.rawCents }),
                    count = days.sumOf { it.count },
                )
            }

        // 2. Consulta 100% real do Room para a tela Início (Etapa 4B-1, sem mistura com PreviewFixtures)
        val zoneId = ZoneId.systemDefault()
        val filteredHomeReal = filterHomeRealSales(realSales, filter, zoneId)
        var homeGrossCents = 0L
        var homeFeeCents = 0L
        var homeNetCents = 0L
        val homePersonAcc = linkedMapOf<String, MutableRealPersonAcc>()
        val homePersonSalesCounts = linkedMapOf<String, Int>()
        registeredPeople.forEach { person ->
            homePersonAcc[person.id] = MutableRealPersonAcc(
                personId = person.id,
                personName = person.name,
                personColorIndex = person.colorIndex,
            )
            homePersonSalesCounts[person.id] = 0
        }

        filteredHomeReal.forEach { persisted ->
            val calc = persisted.calculation
            homeGrossCents = MonetaryRounding.safeAdd(homeGrossCents, calc.finalAmount.rawCents)
            homeFeeCents = MonetaryRounding.safeAdd(homeFeeCents, calc.feeTotal.rawCents)
            homeNetCents = MonetaryRounding.safeAdd(homeNetCents, calc.netTotal.rawCents)

            val countedPeopleInSale = HashSet<String>(calc.personTotals.size)
            calc.personTotals.forEach { personTotal ->
                val acc = homePersonAcc.getOrPut(personTotal.personId) {
                    MutableRealPersonAcc(
                        personId = personTotal.personId,
                        personName = personTotal.personName,
                        personColorIndex = personTotal.personColorIndex,
                    )
                }
                acc.grossCents = MonetaryRounding.safeAdd(acc.grossCents, personTotal.grossAmount.rawCents)
                acc.feeCents = MonetaryRounding.safeAdd(acc.feeCents, personTotal.feeAmount.rawCents)
                acc.netCents = MonetaryRounding.safeAdd(acc.netCents, personTotal.netAmount.rawCents)
                if (countedPeopleInSale.add(personTotal.personId)) {
                    homePersonSalesCounts[personTotal.personId] =
                        (homePersonSalesCounts[personTotal.personId] ?: 0) + 1
                }
            }
        }

        val homeRealPersonSummaries = homePersonAcc.values.map { acc ->
            SalePersonTotalSnapshot(
                personId = acc.personId,
                personName = acc.personName,
                personColorIndex = acc.personColorIndex,
                grossAmount = MoneyCents(acc.grossCents),
                feeAmount = MoneyCents(acc.feeCents),
                netAmount = MoneyCents(acc.netCents),
            )
        }
        val homeDailyReport = DailySalesAggregator.aggregate(filteredHomeReal, zoneId)

        // 2B. Consulta 100% real do Room para a tela Fechamento (Etapa 4B-2, sem mistura com PreviewFixtures)
        val filteredClosingReal = filterSalesByLocalPeriod(
            sales = realSales,
            periodFilter = filter.closingPeriodFilter,
            fromDate = filter.closingPeriodFromDate,
            toDate = filter.closingPeriodToDate,
            zoneId = zoneId,
        )
        var closingOriginalSubtotalCents = 0L
        var closingDiscountCents = 0L
        var closingGrossCents = 0L
        var closingFeeCents = 0L
        var closingNetCents = 0L
        val closingPersonAcc = linkedMapOf<String, MutableRealPersonAcc>()

        filteredClosingReal.forEach { persisted ->
            val calc = persisted.calculation
            closingOriginalSubtotalCents = MonetaryRounding.safeAdd(closingOriginalSubtotalCents, calc.originalSubtotal.rawCents)
            closingDiscountCents = MonetaryRounding.safeAdd(closingDiscountCents, calc.discountTotal.rawCents)
            closingGrossCents = MonetaryRounding.safeAdd(closingGrossCents, calc.finalAmount.rawCents)
            closingFeeCents = MonetaryRounding.safeAdd(closingFeeCents, calc.feeTotal.rawCents)
            closingNetCents = MonetaryRounding.safeAdd(closingNetCents, calc.netTotal.rawCents)

            calc.personTotals.forEach { personTotal ->
                val acc = closingPersonAcc.getOrPut(personTotal.personId) {
                    MutableRealPersonAcc(
                        personId = personTotal.personId,
                        personName = personTotal.personName,
                        personColorIndex = personTotal.personColorIndex,
                    )
                }
                acc.grossCents = MonetaryRounding.safeAdd(acc.grossCents, personTotal.grossAmount.rawCents)
                acc.feeCents = MonetaryRounding.safeAdd(acc.feeCents, personTotal.feeAmount.rawCents)
                acc.netCents = MonetaryRounding.safeAdd(acc.netCents, personTotal.netAmount.rawCents)
            }
        }

        val closingRealPersonSummaries = closingPersonAcc.values
            .sortedWith(
                Comparator { a, b ->
                    val nameCmp = a.personName.lowercase(ptBrLocale).compareTo(b.personName.lowercase(ptBrLocale))
                    if (nameCmp != 0) nameCmp else a.personId.compareTo(b.personId)
                },
            )
            .map { acc ->
                SalePersonTotalSnapshot(
                    personId = acc.personId,
                    personName = acc.personName,
                    personColorIndex = acc.personColorIndex,
                    grossAmount = MoneyCents(acc.grossCents),
                    feeAmount = MoneyCents(acc.feeCents),
                    netAmount = MoneyCents(acc.netCents),
                )
            }

        val closingRealPaymentBreakdowns = SalePaymentMethod.entries.map { method ->
            val subset = filteredClosingReal.filter { it.calculation.paymentMethod == method }
            var methodGross = 0L
            var methodFee = 0L
            var methodNet = 0L
            subset.forEach { persisted ->
                val calc = persisted.calculation
                methodGross = MonetaryRounding.safeAdd(methodGross, calc.finalAmount.rawCents)
                methodFee = MonetaryRounding.safeAdd(methodFee, calc.feeTotal.rawCents)
                methodNet = MonetaryRounding.safeAdd(methodNet, calc.netTotal.rawCents)
            }
            RealPaymentMethodBreakdown(
                method = method,
                count = subset.size,
                gross = MoneyCents(methodGross),
                fee = MoneyCents(methodFee),
                net = MoneyCents(methodNet),
            )
        }

        val closingRealMachineBreakdowns = buildRealMachineBreakdowns(filteredClosingReal)

        // 3. Consulta 100% real do Room para a aba VENDAS (sem mistura com PreviewFixtures)
        val filteredReal = filterRealSales(realSales, filter)
        var realGrossCents = 0L
        var realDiscountCents = 0L
        var realFeeCents = 0L
        var realNetCents = 0L
        val realPersonAcc = linkedMapOf<String, MutableRealPersonAcc>()

        filteredReal.forEach { persisted ->
            val calc = persisted.calculation
            realGrossCents = MonetaryRounding.safeAdd(realGrossCents, calc.finalAmount.rawCents)
            realDiscountCents = MonetaryRounding.safeAdd(realDiscountCents, calc.discountTotal.rawCents)
            realFeeCents = MonetaryRounding.safeAdd(realFeeCents, calc.feeTotal.rawCents)
            realNetCents = MonetaryRounding.safeAdd(realNetCents, calc.netTotal.rawCents)

            calc.personTotals.forEach { personTotal ->
                val acc = realPersonAcc.getOrPut(personTotal.personId) {
                    MutableRealPersonAcc(
                        personId = personTotal.personId,
                        personName = personTotal.personName,
                        personColorIndex = personTotal.personColorIndex,
                    )
                }
                acc.grossCents = MonetaryRounding.safeAdd(acc.grossCents, personTotal.grossAmount.rawCents)
                acc.feeCents = MonetaryRounding.safeAdd(acc.feeCents, personTotal.feeAmount.rawCents)
                acc.netCents = MonetaryRounding.safeAdd(acc.netCents, personTotal.netAmount.rawCents)
            }
        }

        val realPersonSummaries = realPersonAcc.values
            .sortedWith(
                Comparator { a, b ->
                    val nameCmp = a.personName.lowercase(ptBrLocale).compareTo(b.personName.lowercase(ptBrLocale))
                    if (nameCmp != 0) nameCmp else a.personId.compareTo(b.personId)
                },
            )
            .map { acc ->
                SalePersonTotalSnapshot(
                    personId = acc.personId,
                    personName = acc.personName,
                    personColorIndex = acc.personColorIndex,
                    grossAmount = MoneyCents(acc.grossCents),
                    feeAmount = MoneyCents(acc.feeCents),
                    netAmount = MoneyCents(acc.netCents),
                )
            }

        return MixVendasShellUiState(
            roomPreferencesCount = roomPrefCount,
            previewEnabled = filter.previewEnabled,
            periodFilter = filter.periodFilter,
            periodFromDate = filter.periodFromDate,
            periodToDate = filter.periodToDate,
            showAllPeopleOnHome = filter.showAllPeopleOnHome,
            salesSearchQuery = filter.salesSearchQuery,
            salesMethodFilter = filter.salesMethodFilter,
            activeCatalogProducts = activeCatalogProducts,
            catalogProductCategories = catalogProductCategories,
            machines = machines,
            filteredSales = filteredPreview,
            totalGross = MoneyCents(previewGrossCents),
            totalFee = MoneyCents(previewFeeCents),
            totalNet = MoneyCents(previewNetCents),
            personSummaries = previewPersonTotals,
            paymentBreakdowns = paymentBreakdowns,
            machineBreakdowns = machineBreakdowns,
            dayGroups = dayGroups,
            monthGroups = monthGroups,
            homePeriodFilter = filter.homePeriodFilter,
            homePeriodFromDate = filter.homePeriodFromDate,
            homePeriodToDate = filter.homePeriodToDate,
            homeRealTotalSalesCount = realSales.size,
            homeFilteredRealSales = filteredHomeReal,
            homeFilteredRealSalesCount = filteredHomeReal.size,
            homeRealTotalGross = MoneyCents(homeGrossCents),
            homeRealTotalFee = MoneyCents(homeFeeCents),
            homeRealTotalNet = MoneyCents(homeNetCents),
            homeRealPersonSummaries = homeRealPersonSummaries,
            homeRealPersonSalesCounts = homePersonSalesCounts,
            homeDailyReport = homeDailyReport,
            closingPeriodFilter = filter.closingPeriodFilter,
            closingPeriodFromDate = filter.closingPeriodFromDate,
            closingPeriodToDate = filter.closingPeriodToDate,
            closingRealTotalSalesCount = realSales.size,
            closingFilteredRealSales = filteredClosingReal,
            closingFilteredRealSalesCount = filteredClosingReal.size,
            closingRealOriginalSubtotal = MoneyCents(closingOriginalSubtotalCents),
            closingRealTotalDiscount = MoneyCents(closingDiscountCents),
            closingRealTotalGross = MoneyCents(closingGrossCents),
            closingRealTotalFee = MoneyCents(closingFeeCents),
            closingRealTotalNet = MoneyCents(closingNetCents),
            closingRealPersonSummaries = closingRealPersonSummaries,
            closingRealPaymentBreakdowns = closingRealPaymentBreakdowns,
            closingRealMachineBreakdowns = closingRealMachineBreakdowns,
            realSalesCount = realSales.size,
            filteredRealSales = filteredReal,
            realTotalGross = MoneyCents(realGrossCents),
            realTotalDiscount = MoneyCents(realDiscountCents),
            realTotalFee = MoneyCents(realFeeCents),
            realTotalNet = MoneyCents(realNetCents),
            realPersonSummaries = realPersonSummaries,
            realDailyReport = realDailyReport,
            selectedSalesDate = filter.selectedSalesDate,
            saleDeleteState = salesAction.deleteState,
            salesFeedbackMessage = salesAction.feedbackMessage,
            salesFeedbackIsError = salesAction.feedbackIsError,
            closingCsvExportState = closingCsvExport,
            venderForm = venderForm,
        )
    }

    private fun buildRealMachineBreakdowns(sales: List<PersistedSale>): List<RealMachineBreakdown> {
        val cardSales = sales.filter { it.calculation.paymentMethod.usesMachine }
        if (cardSales.isEmpty()) return emptyList()

        data class MachineGroupKey(
            val key: String,
            val machineId: String?,
            val machineName: String,
            val method: SalePaymentMethod,
        )

        return cardSales
            .groupBy { persisted ->
                val calc = persisted.calculation
                val snap = calc.machineSnapshot
                val mId = snap?.machineId
                val mName = snap?.machineName ?: "Sem maquininha"
                MachineGroupKey(
                    key = "${mId ?: "none"}|$mName|${calc.paymentMethod.storageKey}",
                    machineId = mId,
                    machineName = mName,
                    method = calc.paymentMethod,
                )
            }
            .entries
            .sortedWith(
                Comparator { a, b ->
                    val nameCmp = a.key.machineName.lowercase(ptBrLocale)
                        .compareTo(b.key.machineName.lowercase(ptBrLocale))
                    if (nameCmp != 0) {
                        nameCmp
                    } else {
                        val methodCmp = a.key.method.ordinal.compareTo(b.key.method.ordinal)
                        if (methodCmp != 0) methodCmp else a.key.key.compareTo(b.key.key)
                    }
                },
            )
            .map { (groupKey, subset) ->
                var groupGross = 0L
                var groupFee = 0L
                var groupNet = 0L
                val distinctRates = subset.map { it.calculation.effectiveFeeRateBps }.distinct().sorted()
                val anyManual = subset.any { it.calculation.machineSnapshot?.manualRateOverrideBps != null }

                subset.forEach { persisted ->
                    val calc = persisted.calculation
                    groupGross = MonetaryRounding.safeAdd(groupGross, calc.finalAmount.rawCents)
                    groupFee = MonetaryRounding.safeAdd(groupFee, calc.feeTotal.rawCents)
                    groupNet = MonetaryRounding.safeAdd(groupNet, calc.netTotal.rawCents)
                }

                val singleRateLabel = if (distinctRates.size == 1) {
                    val rateFormatted = RateFormatter.formatBpsAsPercent(distinctRates.first())
                    val allManual = subset.all { it.calculation.machineSnapshot?.manualRateOverrideBps != null }
                    if (allManual) {
                        "$rateFormatted (tarifa manual)"
                    } else if (groupKey.method == SalePaymentMethod.CREDIT) {
                        val distinctBrands = subset.map { it.calculation.machineSnapshot?.appliedBrandDisplayLabel }.distinct()
                        val distinctInst = subset.map { it.calculation.machineSnapshot?.appliedInstallments }.distinct()
                        val brandLabel = distinctBrands.singleOrNull()
                        val inst = distinctInst.singleOrNull()
                        buildString {
                            if (brandLabel != null) {
                                append(brandLabel)
                                append(" · ")
                            }
                            if (inst != null && inst > 1) {
                                append("${inst}x · ")
                            }
                            append(rateFormatted)
                        }
                    } else {
                        rateFormatted
                    }
                } else {
                    null
                }

                RealMachineBreakdown(
                    machineKey = groupKey.key,
                    machineId = groupKey.machineId,
                    machineName = groupKey.machineName,
                    paymentMethod = groupKey.method,
                    count = subset.size,
                    gross = MoneyCents(groupGross),
                    fee = MoneyCents(groupFee),
                    net = MoneyCents(groupNet),
                    distinctEffectiveRatesBps = distinctRates,
                    hasManualOverride = anyManual,
                    singleRateSummaryLabel = singleRateLabel,
                )
            }
    }

    private fun filterHomeRealSales(
        sales: List<PersistedSale>,
        filter: FilterState,
        zoneId: ZoneId,
    ): List<PersistedSale> = filterSalesByLocalPeriod(
        sales = sales,
        periodFilter = filter.homePeriodFilter,
        fromDate = filter.homePeriodFromDate,
        toDate = filter.homePeriodToDate,
        zoneId = zoneId,
    )

    private fun filterSalesByLocalPeriod(
        sales: List<PersistedSale>,
        periodFilter: PreviewPeriodFilter,
        fromDate: String,
        toDate: String,
        zoneId: ZoneId,
    ): List<PersistedSale> {
        val today = LocalDate.now(zoneId)
        val fromParsed = parseOptionalIsoDate(fromDate)
        val toParsed = parseOptionalIsoDate(toDate)
        return sales.filter { persisted ->
            val saleDate = DailySalesAggregator.toLocalDate(persisted.soldAtEpochMs, zoneId)
            matchesPeriodDate(saleDate, today, periodFilter, fromParsed, toParsed)
        }.sortedWith(
            compareByDescending<PersistedSale> { it.soldAtEpochMs }.thenBy { it.id },
        )
    }

    private fun filterRealSales(
        sales: List<PersistedSale>,
        filter: FilterState,
    ): List<PersistedSale> {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val selectedDayRange = filter.selectedSalesDate?.let { selectedDate ->
            DailySalesAggregator.localDayEpochRange(selectedDate, zoneId)
        }
        val fromParsed = parseOptionalIsoDate(filter.periodFromDate)
        val toParsed = parseOptionalIsoDate(filter.periodToDate)
        val search = filter.salesSearchQuery.trim().lowercase(ptBrLocale)

        return sales.filter { persisted ->
            val calc = persisted.calculation
            val matchesPeriod = if (selectedDayRange != null) {
                persisted.soldAtEpochMs >= selectedDayRange.first &&
                    persisted.soldAtEpochMs < selectedDayRange.second
            } else {
                val saleDate = Instant.ofEpochMilli(persisted.soldAtEpochMs).atZone(zoneId).toLocalDate()
                matchesPeriodDate(saleDate, today, filter.periodFilter, fromParsed, toParsed)
            }
            val matchesMethod = filter.salesMethodFilter == null ||
                calc.paymentMethod == filter.salesMethodFilter.toSalePaymentMethod()
            val matchesSearch = search.isEmpty() || buildString {
                append(calc.paymentMethod.label)
                append(' ')
                append(calc.note)
                append(' ')
                append(calc.machineSnapshot?.machineName.orEmpty())
                append(' ')
                append(calc.machineSnapshot?.appliedBrandDisplayLabel.orEmpty())
                calc.items.forEach { item ->
                    append(' ')
                    append(item.productName)
                }
                calc.personTotals.forEach { person ->
                    append(' ')
                    append(person.personName)
                }
            }.lowercase(ptBrLocale).contains(search)

            matchesPeriod && matchesMethod && matchesSearch
        }.sortedWith(
            compareByDescending<PersistedSale> { it.soldAtEpochMs }.thenBy { it.id },
        )
    }

    private fun filterPreviewSales(
        sales: List<PreviewSale>,
        filter: FilterState,
    ): List<PreviewSale> {
        val today = LocalDate.now()
        val fromParsed = parseOptionalIsoDate(filter.periodFromDate)
        val toParsed = parseOptionalIsoDate(filter.periodToDate)
        val search = filter.salesSearchQuery.trim().lowercase(ptBrLocale)

        return sales.filter { sale ->
            val matchesPeriod = matchesPeriodDate(sale.date, today, filter.periodFilter, fromParsed, toParsed)
            val matchesMethod = filter.salesMethodFilter == null || sale.method == filter.salesMethodFilter
            val matchesSearch = search.isEmpty() || buildString {
                append(sale.title)
                append(' ')
                append(sale.machineName.orEmpty())
                append(' ')
                append(sale.note)
                sale.items.forEach {
                    append(' ')
                    append(it.productName)
                }
            }.lowercase(ptBrLocale).contains(search)

            matchesPeriod && matchesMethod && matchesSearch
        }.sortedByDescending { it.date }
    }

    private fun matchesPeriodDate(
        saleDate: LocalDate,
        today: LocalDate,
        periodFilter: PreviewPeriodFilter,
        fromParsed: LocalDate?,
        toParsed: LocalDate?,
    ): Boolean {
        return when (periodFilter) {
            PreviewPeriodFilter.TODAY -> saleDate == today
            PreviewPeriodFilter.WEEK -> !saleDate.isBefore(today.minusDays(6)) && !saleDate.isAfter(today)
            PreviewPeriodFilter.MONTH -> saleDate.year == today.year && saleDate.month == today.month
            PreviewPeriodFilter.ALL -> true
            PreviewPeriodFilter.CUSTOM -> {
                val afterFrom = fromParsed == null || !saleDate.isBefore(fromParsed)
                val beforeTo = toParsed == null || !saleDate.isAfter(toParsed)
                afterFrom && beforeTo
            }
        }
    }

    private fun PreviewPaymentMethod.toSalePaymentMethod(): SalePaymentMethod = when (this) {
        PreviewPaymentMethod.CASH -> SalePaymentMethod.CASH
        PreviewPaymentMethod.PIX -> SalePaymentMethod.PIX
        PreviewPaymentMethod.DEBIT -> SalePaymentMethod.DEBIT
        PreviewPaymentMethod.CREDIT -> SalePaymentMethod.CREDIT
    }

    private fun SalePaymentMethod.toPreviewPaymentMethod(): PreviewPaymentMethod = when (this) {
        SalePaymentMethod.CASH -> PreviewPaymentMethod.CASH
        SalePaymentMethod.PIX -> PreviewPaymentMethod.PIX
        SalePaymentMethod.DEBIT -> PreviewPaymentMethod.DEBIT
        SalePaymentMethod.CREDIT -> PreviewPaymentMethod.CREDIT
    }

    private fun parseOptionalIsoDate(value: String): LocalDate? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { LocalDate.parse(trimmed) }.getOrNull()
    }

    private class MutableRealPersonAcc(
        val personId: String,
        val personName: String,
        val personColorIndex: Int,
        var grossCents: Long = 0L,
        var feeCents: Long = 0L,
        var netCents: Long = 0L,
    )

    companion object {
        fun resolvedProductsMap(
            form: VenderFormUiState,
            activeCatalogProducts: List<Product>,
        ): Map<String, Product> {
            val activeMap = activeCatalogProducts.associateBy { it.id }
            return if (form.historicalProductsById.isEmpty()) {
                activeMap
            } else {
                activeMap + form.historicalProductsById
            }
        }

        fun pickerCatalogProducts(
            form: VenderFormUiState,
            activeCatalogProducts: List<Product>,
        ): List<Product> {
            if (form.historicalProductsById.isEmpty()) return activeCatalogProducts
            val historicalList = form.historicalProductsById.values.toList()
            val extraActive = activeCatalogProducts.filterNot { form.historicalProductsById.containsKey(it.id) }
            return historicalList + extraActive
        }

        fun canUseHistoricalMachineSnapshot(form: VenderFormUiState): Boolean {
            val origMethod = form.originalEditingSale?.calculation?.paymentMethod ?: return false
            val snap = form.historicalMachineSnapshot ?: return false
            val currentMethod = when (form.selectedMethod) {
                PreviewPaymentMethod.CASH -> SalePaymentMethod.CASH
                PreviewPaymentMethod.PIX -> SalePaymentMethod.PIX
                PreviewPaymentMethod.DEBIT -> SalePaymentMethod.DEBIT
                PreviewPaymentMethod.CREDIT -> SalePaymentMethod.CREDIT
            }
            return form.isEditing &&
                form.preferHistoricalMachineSnapshot &&
                currentMethod == origMethod &&
                form.selectedMachineId == snap.machineId
        }

        fun resolvedSelectedMachine(
            form: VenderFormUiState,
            machines: List<Machine>,
        ): Machine? {
            val usesMachine = form.selectedMethod == PreviewPaymentMethod.DEBIT ||
                form.selectedMethod == PreviewPaymentMethod.CREDIT
            if (!usesMachine) return null
            if (canUseHistoricalMachineSnapshot(form)) {
                val orig = form.originalEditingSale
                val paymentMethod = when (form.selectedMethod) {
                    PreviewPaymentMethod.DEBIT -> SalePaymentMethod.DEBIT
                    PreviewPaymentMethod.CREDIT -> SalePaymentMethod.CREDIT
                    PreviewPaymentMethod.CASH -> SalePaymentMethod.CASH
                    PreviewPaymentMethod.PIX -> SalePaymentMethod.PIX
                }
                return form.historicalMachineSnapshot!!.toSnapshotMachine(
                    paymentMethod = paymentMethod,
                    soldAtEpochMs = orig?.soldAtEpochMs ?: 0L,
                    updatedAtEpochMs = orig?.updatedAtEpochMs ?: 0L,
                )
            }
            val eligibleMachines = when (form.selectedMethod) {
                PreviewPaymentMethod.DEBIT -> machines
                PreviewPaymentMethod.CREDIT -> machines.filter { it.creditEnabled }
                PreviewPaymentMethod.CASH,
                PreviewPaymentMethod.PIX -> emptyList()
            }
            return eligibleMachines.firstOrNull { it.id == form.selectedMachineId }
        }

        fun computeSelectedSubtotalCents(
            selectedQuantities: Map<String, Int>,
            products: List<Product>,
        ): Long = computeSelectedSubtotalCents(
            selectedQuantities = selectedQuantities,
            productMap = products.associateBy { it.id },
        )

        fun computeSelectedSubtotalCentsOrNull(
            selectedQuantities: Map<String, Int>,
            productMap: Map<String, Product>,
        ): Long? {
            var total = 0L
            for ((id, qty) in selectedQuantities.entries) {
                val safeQty = qty.coerceAtLeast(0)
                if (safeQty == 0) continue
                val price = productMap[id]?.price?.rawCents ?: 0L
                val lineTotal = MonetaryRounding.safeMultiplyOrNull(price, safeQty) ?: return null
                total = MonetaryRounding.safeAddOrNull(total, lineTotal) ?: return null
            }
            return total
        }

        fun computeSelectedSubtotalCents(
            selectedQuantities: Map<String, Int>,
            productMap: Map<String, Product>,
        ): Long {
            return computeSelectedSubtotalCentsOrNull(selectedQuantities, productMap) ?: Long.MAX_VALUE
        }

        fun parseBrlInputToCents(input: String): Long? =
            CurrencyFormatter.parseBrlToCents(input)

        fun provideFactory(
            preferenceRepository: PreferenceRepository,
            catalogRepository: CatalogRepository,
            machineRepository: MachineRepository,
            saleRepository: SaleRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MixVendasShellViewModel(
                    preferenceRepository = preferenceRepository,
                    catalogRepository = catalogRepository,
                    machineRepository = machineRepository,
                    saleRepository = saleRepository,
                ) as T
            }
        }
    }
}
