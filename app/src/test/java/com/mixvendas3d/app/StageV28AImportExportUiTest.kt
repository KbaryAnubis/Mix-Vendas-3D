package com.mixvendas3d.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageV28AImportExportUiTest {

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

    private fun newTempFile(name: String, bytes: ByteArray? = null): File {
        val file = File(composeRule.activity.cacheDir, name)
        file.delete()
        if (bytes != null) {
            file.writeBytes(bytes)
        }
        tempFiles.add(file)
        return file
    }

    private fun waitUiUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun openImportExportScreen() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mais_item_import_export")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais_detail_import_export").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun seedMinimalSale() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana V28A")
            val prod = app.catalogRepository.createProduct(
                name = "Suporte V28A",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Point V28A",
                    debitRateBps = 199,
                    creditEnabled = false,
                    creditDefaultRateBps = 0,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    finalAmountOverride = null,
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                    cardBrand = null,
                    installments = 1,
                    note = "Venda V2-8A",
                ),
            )
            app.saleRepository.saveSale(calc)
        }
    }

    @Test
    fun `01 header V2 renders title without technical overline and back button returns to Mais`() {
        openImportExportScreen()

        composeRule.onNodeWithTag("topbar_title")
            .assertIsDisplayed()
            .assertTextContains("Importar / exportar")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)
        composeRule.onAllNodesWithText("BACKUP & DADOS", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("ETAPA", substring = true, ignoreCase = true)
            .assertCountEquals(0)

        composeRule.onNodeWithTag("topbar_back_button")
            .assertIsDisplayed()
            .performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(0)
    }

    @Test
    fun `02 main card renders V2 sections, both action buttons, non-clickable 3-step guide, and offline security note`() {
        openImportExportScreen()

        composeRule.onNodeWithTag("backup_restore_card").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_restore_title")
            .assertIsDisplayed()
            .assertTextContains("Backup e restauração")
        composeRule.onNodeWithTag("backup_restore_subtitle")
            .assertIsDisplayed()
            .assertTextContains(
                "Salve e recupere seus cadastros, vendas, maquininhas e histórico financeiro com segurança.",
            )

        composeRule.onNodeWithText("Exportar backup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backup_export_description")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Gere um arquivo JSON com os dados atuais do aplicativo.")
        composeRule.onNodeWithTag("export_json_backup_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("Exportar backup JSON")

        composeRule.onNodeWithText("Importar / Restaurar backup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backup_select_description")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Selecione um arquivo JSON para analisar os dados antes de restaurar.")
        composeRule.onNodeWithTag("select_json_backup_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("Selecionar backup JSON")

        // Fluxo explicativo em 3 passos (apenas informativo, sem clique falso)
        composeRule.onNodeWithTag("backup_steps_guide").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backup_step_1")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .assertTextContains("1 — Analisar", substring = true)
        composeRule.onNodeWithTag("backup_step_2")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .assertTextContains("2 — Revisar conflitos", substring = true)
        composeRule.onNodeWithTag("backup_step_3")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasNoClickAction()
            .assertTextContains("3 — Restaurar", substring = true)

        composeRule.onNodeWithTag("backup_security_note")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(
                "O backup é gerado e analisado localmente. A restauração exige revisão e confirmação antes de alterar os dados.",
            )

        // Sem botão de restauração direta nesta etapa
        composeRule.onAllNodesWithTag("restore_backup_button").assertCountEquals(0)
    }

    @Test
    fun `03 export button triggers SAF CreateDocument and blocks concurrent clicks while busy`() {
        seedMinimalSale()
        openImportExportScreen()

        composeRule.onNodeWithTag("export_json_backup_button")
            .performScrollTo()
            .performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }

        // Bloqueia cliques concorrentes nos dois botões enquanto SAF está aberto
        composeRule.onNodeWithTag("export_json_backup_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("select_json_backup_button").assertIsNotEnabled()

        val started = shadowActivity.nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
        assertEquals("application/json", started.intent.type)

        val outFile = newTempFile("v28a_export.json")
        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(outFile)),
        )

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Backup JSON salvo com sucesso.", substring = true)
        composeRule.onNodeWithTag("export_json_backup_button").assertIsEnabled()
        composeRule.onNodeWithTag("select_json_backup_button").assertIsEnabled()
        assertTrue(JsonBackupReader.readAndValidate(outFile.readBytes()).isValid)
    }

    @Test
    fun `04 select backup button triggers SAF OpenDocument and preserves conflict and preview downstream sections`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val createdPerson = runBlocking(Dispatchers.IO) {
            app.catalogRepository.createPerson("Pessoa Backup V28A")
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val backupFile = newTempFile("v28a_conflict.json", exported.utf8Bytes)

        // Gera diferença local real para exercitar comparação, conflitos e prévia
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.renamePerson(createdPerson.id, "Pessoa Local V28A")
        }

        openImportExportScreen()

        composeRule.onNodeWithTag("select_json_backup_button")
            .performScrollTo()
            .performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        composeRule.onNodeWithTag("export_json_backup_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("select_json_backup_button").assertIsNotEnabled()

        val started = shadowActivity.nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(backupFile)),
        )

        waitUiUntil {
            composeRule.onAllNodesWithTag("conflict_resolution_section").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("selected_backup_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("comparison_result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("conflict_resolution_section").performScrollTo().assertIsDisplayed()

        // Decide conflito e abre prévia preservada da etapa 6D-3B/6D-4
        val unitKey = "people:${createdPerson.id}"
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_KEEP_LOCAL")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("review_restore_button")
            .performScrollTo()
            .performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("preview_restore_section").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("preview_restore_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("restore_backup_button").performScrollTo().assertIsEnabled()

        // Garante que nenhuma restauração foi executada sem confirmação explícita
        val currentPerson = runBlocking(Dispatchers.IO) {
            app.database.personDao().getAllPeople().first { it.id == createdPerson.id }
        }
        assertEquals("Pessoa Local V28A", currentPerson.name)
    }

    @Test
    fun `05 cancel and error feedback banners display clearly and recover on retry`() {
        openImportExportScreen()
        val shadowActivity = Shadows.shadowOf(composeRule.activity)

        // Cancelamento na seleção
        composeRule.onNodeWithTag("select_json_backup_button").performScrollTo().performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val openStarted = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(openStarted.intent, Activity.RESULT_CANCELED, null)

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_analysis_feedback_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Seleção de backup cancelada.", substring = true)

        // Arquivo inválido seguido de retry com cancelamento na exportação
        val invalidFile = newTempFile("v28a_invalid.json", "{corrompido".toByteArray(Charsets.UTF_8))
        composeRule.onNodeWithTag("select_json_backup_button").performScrollTo().performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val retryOpen = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(retryOpen.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(invalidFile)))

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("backup_analysis_feedback_banner")
                    .assertTextContains("Arquivo de backup inválido.", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("select_json_backup_button").assertIsEnabled()
        composeRule.onNodeWithTag("export_json_backup_button").assertIsEnabled()
    }
}
