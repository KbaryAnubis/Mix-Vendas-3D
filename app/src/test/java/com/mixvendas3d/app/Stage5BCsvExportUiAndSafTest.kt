package com.mixvendas3d.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.CsvExportPeriod
import com.mixvendas3d.app.domain.export.CsvSaleExporter
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage5BCsvExportUiAndSafTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun newTempCsvFile(name: String): File {
        val file = File(composeRule.activity.cacheDir, name)
        file.delete()
        tempFiles.add(file)
        return file
    }

    private fun waitUiUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun openFechamento() {
        composeRule.onNodeWithTag("nav_tab_fechamento").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_fechamento").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun todaySuffix(): String =
        LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    private suspend fun insertRealSaleAt(
        app: MixVendasApplication,
        epochMs: Long,
        items: List<SaleItemInput>,
        finalAmountOverride: MoneyCents? = null,
        discountMode: SaleDiscountInput = SaleDiscountInput.Proportional,
        paymentMethod: SalePaymentMethod = SalePaymentMethod.PIX,
        machineId: String? = null,
        cardBrand: CardBrand? = null,
        installments: Int? = null,
        note: String = "",
    ): String {
        val machine = if (machineId != null) {
            app.machineRepository.observeMachines().first().first { it.id == machineId }
        } else {
            null
        }
        val repo = RoomSaleRepository(
            database = app.database,
            saleDao = app.database.saleDao(),
            clockMillis = { epochMs },
        )
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = items,
                finalAmountOverride = finalAmountOverride,
                discountMode = discountMode,
                paymentMethod = paymentMethod,
                machine = machine,
                cardBrand = cardBrand,
                installments = installments,
                note = note,
            ),
        )
        return repo.saveSale(calc).id
    }

    private fun seedTwoDaySales(): Triple<String, String, String> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayMs = today.atTime(10, 15, 0).atZone(zone).toInstant().toEpochMilli()
        val oldMs = today.minusDays(25).atTime(14, 30, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        return runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana Export")
            val bruno = app.catalogRepository.createPerson("Bruno Export")
            val prod = app.catalogRepository.createProduct(
                name = "Suporte Exportação",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Export",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 500),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            val todaySaleId = insertRealSaleAt(
                app = app,
                epochMs = todayMs,
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = machine.id,
                cardBrand = CardBrand.VISA,
                installments = 1,
                note = "Venda Hoje Real",
            )
            val oldSaleId = insertRealSaleAt(
                app = app,
                epochMs = oldMs,
                items = listOf(SaleItemInput(prod, 2)),
                finalAmountOverride = MoneyCents(18_000L),
                paymentMethod = SalePaymentMethod.PIX,
                note = "Venda Antiga Real",
            )
            Triple(todaySaleId, oldSaleId, prod.id)
        }
    }

    @Test
    fun `01 empty fechamento keeps button enabled, informs empty period and does not launch SAF`() {
        openFechamento()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        composeRule.onNodeWithTag("export_csv_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("closing_csv_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("closing_csv_feedback_banner")
            .assertTextContains("Nenhuma venda encontrada no período selecionado.", substring = true)

        composeRule.onAllNodesWithTag("csv_export_dialog").assertCountEquals(0)
        assertNull(shadowActivity.peekNextStartedActivityForResult())
    }

    @Test
    fun `02 summary option exports real sales, BOM, exact bytes, suggested filename and keeps Room intact`() {
        val (todaySaleId, oldSaleId, _) = seedTwoDaySales()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val salesBefore = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        val expectedExport = CsvSaleExporter.export(
            sales = salesBefore,
            period = CsvExportPeriod(),
            zoneId = ZoneId.systemDefault(),
            generatedOnDate = LocalDate.now(ZoneId.systemDefault()),
        )

        openFechamento()
        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("csv_export_dialog_title")
            .assertTextContains("Exportar vendas")
        composeRule.onNodeWithTag("csv_export_option_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_option_detailed").assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_option_both").assertIsDisplayed()

        composeRule.onNodeWithTag("csv_export_option_summary").performClick()
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil {
            shadowActivity.peekNextStartedActivityForResult() != null
        }

        val started = shadowActivity.nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
        assertEquals("text/csv", started.intent.type)
        assertEquals("mix_vendas_3d_resumo_${todaySuffix()}.csv", started.intent.getStringExtra(Intent.EXTRA_TITLE))

        val outFile = newTempCsvFile("resumo_out.csv")
        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(outFile)),
        )

        waitUiUntil {
            composeRule.onAllNodesWithTag("closing_csv_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("closing_csv_feedback_banner")
            .assertTextContains("CSV exportado com sucesso.", substring = true)

        val writtenBytes = outFile.readBytes()
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            writtenBytes.copyOfRange(0, 3),
        )
        assertArrayEquals(expectedExport.summaryCsvBytes(), writtenBytes)
        val writtenText = String(writtenBytes, Charsets.UTF_8)
        assertTrue(writtenText.contains(todaySaleId))
        assertTrue(writtenText.contains(oldSaleId))
        assertFalse(writtenText.contains("Prévia"))

        val salesAfter = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        assertEquals(salesBefore, salesAfter)
    }

    @Test
    fun `03 detailed option respects fechamento period filter independently and writes exact detailed bytes`() {
        val (todaySaleId, oldSaleId, _) = seedTwoDaySales()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        openFechamento()

        // Seleciona "Hoje" apenas em Fechamento
        composeRule.onNodeWithTag("period_dropdown_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_today").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card").assertTextContains("1 venda", substring = true)
                true
            }.getOrDefault(false)
        }

        val allSales = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        val expectedDetailed = CsvSaleExporter.export(
            sales = allSales,
            period = CsvExportPeriod(fromDate = today, toDate = today),
            zoneId = zone,
            generatedOnDate = today,
        )

        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("csv_export_option_detailed").performClick()
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil {
            shadowActivity.peekNextStartedActivityForResult() != null
        }
        val started = shadowActivity.nextStartedActivityForResult
        assertEquals("mix_vendas_3d_detalhado_${todaySuffix()}.csv", started.intent.getStringExtra(Intent.EXTRA_TITLE))

        val outFile = newTempCsvFile("detalhado_hoje.csv")
        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(outFile)),
        )

        waitUiUntil {
            composeRule.onAllNodesWithTag("closing_csv_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("closing_csv_feedback_banner")
            .assertTextContains("CSV exportado com sucesso.", substring = true)

        val writtenBytes = outFile.readBytes()
        assertArrayEquals(expectedDetailed.detailedCsvBytes(), writtenBytes)
        val writtenText = String(writtenBytes, Charsets.UTF_8)
        assertTrue(writtenText.contains(todaySaleId))
        assertFalse(writtenText.contains(oldSaleId))
    }

    @Test
    fun `04 export both saves summary first, then detailed, and confirms both files`() {
        seedTwoDaySales()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val allSales = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        val expected = CsvSaleExporter.export(
            sales = allSales,
            period = CsvExportPeriod(),
            zoneId = ZoneId.systemDefault(),
            generatedOnDate = LocalDate.now(ZoneId.systemDefault()),
        )

        openFechamento()
        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("csv_export_option_both").performClick()
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }

        // 1º seletor: Resumo
        val req1 = shadowActivity.nextStartedActivityForResult
        assertEquals("mix_vendas_3d_resumo_${todaySuffix()}.csv", req1.intent.getStringExtra(Intent.EXTRA_TITLE))
        val summaryFile = newTempCsvFile("both_resumo.csv")
        shadowActivity.receiveResult(req1.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(summaryFile)))

        // 2º seletor: Detalhamento (aberto somente após salvar o 1º!)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val req2 = shadowActivity.nextStartedActivityForResult
        assertEquals("mix_vendas_3d_detalhado_${todaySuffix()}.csv", req2.intent.getStringExtra(Intent.EXTRA_TITLE))
        val detailedFile = newTempCsvFile("both_detalhado.csv")
        shadowActivity.receiveResult(req2.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(detailedFile)))

        waitUiUntil {
            composeRule.onAllNodesWithTag("closing_csv_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("closing_csv_feedback_banner")
            .assertTextContains("CSV exportado com sucesso.", substring = true)
            .assertTextContains("Resumo das vendas", substring = true, ignoreCase = true)
            .assertTextContains("detalhamento completo", substring = true, ignoreCase = true)

        assertArrayEquals(expected.summaryCsvBytes(), summaryFile.readBytes())
        assertArrayEquals(expected.detailedCsvBytes(), detailedFile.readBytes())
    }

    @Test
    fun `05 canceling dialog, canceling first SAF, and canceling second SAF preserve first file and avoid false success`() {
        seedTwoDaySales()
        openFechamento()

        // A) Cancelar diálogo
        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil { composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("csv_export_cancel_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("csv_export_dialog").assertCountEquals(0)
        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        assertNull(shadowActivity.peekNextStartedActivityForResult())

        // B) Cancelar 1º SAF
        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil { composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("csv_export_option_summary").performClick()
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val firstCancelReq = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(firstCancelReq.intent, Activity.RESULT_CANCELED, null)

        waitUiUntil {
            composeRule.onAllNodesWithTag("closing_csv_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("closing_csv_feedback_banner")
            .assertTextContains("Exportação CSV cancelada.", substring = true)

        // C) Exportar ambos e cancelar 2º SAF: preserva o 1º arquivo e informa exportação parcial!
        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil { composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("csv_export_option_both").performClick()
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }

        val bothReq1 = shadowActivity.nextStartedActivityForResult
        val preservedSummary = newTempCsvFile("partial_resumo.csv")
        shadowActivity.receiveResult(bothReq1.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(preservedSummary)))

        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val bothReq2 = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(bothReq2.intent, Activity.RESULT_CANCELED, null)

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("closing_csv_feedback_banner")
                    .assertTextContains("Exportação parcial:", substring = true)
                true
            }.getOrDefault(false)
        }
        assertTrue(preservedSummary.exists())
        assertTrue(preservedSummary.length() > 0L)
    }

    @Test
    fun `06 write failure shows error, does not report success, protects double taps and allows retry`() {
        seedTwoDaySales()
        openFechamento()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil { composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("csv_export_option_summary").performClick()
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()

        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        // Proteção contra clique repetido enquanto SAF aguarda retorno
        composeRule.onNodeWithTag("export_csv_button").assertExists()
        val reqFail = shadowActivity.nextStartedActivityForResult
        assertNull(shadowActivity.peekNextStartedActivityForResult())

        // URI inválida sem ContentProvider -> falha de gravação
        shadowActivity.receiveResult(
            reqFail.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://com.mixvendas3d.invalid.provider/fail.csv")),
        )

        waitUiUntil {
            composeRule.onAllNodesWithTag("closing_csv_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("closing_csv_feedback_banner")
            .assertTextContains("Não foi possível salvar o arquivo CSV", substring = true)

        // Permite nova tentativa com sucesso
        composeRule.onNodeWithTag("export_csv_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        waitUiUntil { composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val reqRetry = shadowActivity.nextStartedActivityForResult
        val retryFile = newTempCsvFile("retry_ok.csv")
        shadowActivity.receiveResult(reqRetry.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(retryFile)))

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("closing_csv_feedback_banner")
                    .assertTextContains("CSV exportado com sucesso.", substring = true)
                true
            }.getOrDefault(false)
        }
        assertTrue(retryFile.exists() && retryFile.length() > 0L)
    }

    @Test
    @Config(sdk = [26])
    fun `07 createDocument contract creates compatible SAF intent on Android minSdk 26`() {
        val context = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val contract = ActivityResultContracts.CreateDocument("text/csv")
        val intent = contract.createIntent(context, "mix_vendas_3d_resumo_20261117.csv")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("text/csv", intent.type)
        assertEquals("mix_vendas_3d_resumo_20261117.csv", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun `08 export both failure on second file preserves first saved file and informs partial error`() {
        seedTwoDaySales()
        openFechamento()

        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil { composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("csv_export_option_both").performClick()
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val req1 = shadowActivity.nextStartedActivityForResult
        val firstFile = newTempCsvFile("both_partial_first.csv")
        shadowActivity.receiveResult(req1.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(firstFile)))

        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val req2 = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(
            req2.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://com.mixvendas3d.invalid.provider/fail_second.csv")),
        )

        waitUiUntil {
            composeRule.onAllNodesWithTag("closing_csv_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("closing_csv_feedback_banner")
            .assertTextContains("Exportação parcial:", substring = true)
            .assertTextContains("erro ao gravar o detalhamento completo", substring = true)
        assertTrue(firstFile.exists() && firstFile.length() > 0L)
    }

    @Test
    fun `09 activity recreation preserves csv export dialog selection and prevents duplicate launches`() {
        seedTwoDaySales()
        openFechamento()

        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil { composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("csv_export_option_detailed").performClick()

        // Simula recriação da Activity com diálogo aberto
        composeRule.activityRule.scenario.recreate()
        waitUiUntil {
            composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val req = shadowActivity.nextStartedActivityForResult
        assertEquals("mix_vendas_3d_detalhado_${todaySuffix()}.csv", req.intent.getStringExtra(Intent.EXTRA_TITLE))
        assertNull(shadowActivity.peekNextStartedActivityForResult())

        val outFile = newTempCsvFile("recreate_detailed.csv")
        shadowActivity.receiveResult(req.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(outFile)))

        waitUiUntil {
            composeRule.onAllNodesWithTag("closing_csv_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("closing_csv_feedback_banner")
            .assertTextContains("CSV exportado com sucesso.", substring = true)
        assertTrue(outFile.exists() && outFile.length() > 0L)
    }
}
