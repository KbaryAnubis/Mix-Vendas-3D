package com.mixvendas3d.app

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.ui.screens.StartupDiagnosticRoute
import com.mixvendas3d.app.ui.screens.StartupDiagnosticViewModel
import com.mixvendas3d.app.ui.theme.MixVendas3DTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class Stage1RobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app metadata name and offline manifest permissions match stage 1 requirements`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("MIX VENDAS 3D", context.getString(R.string.app_name))
        assertEquals("com.mixvendas3d.app", context.packageName)

        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requestedPermissions = packageInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(
            "App deve ser 100% offline sem android.permission.INTERNET",
            requestedPermissions.contains(Manifest.permission.INTERNET),
        )
    }

    @Test
    fun `android auto backup is disabled while preserving package id and offline permissions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appInfo = context.applicationInfo
        assertEquals("com.mixvendas3d.app", appInfo.packageName)
        assertEquals(
            "Backup automático do sistema (FLAG_ALLOW_BACKUP) deve estar desativado",
            0,
            appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )

        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requestedPermissions = packageInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(
            "App deve permanecer 100% offline sem android.permission.INTERNET",
            requestedPermissions.contains(Manifest.permission.INTERNET),
        )
    }

    @Test
    fun `room preference database starts empty and persists across close and reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "restart.db"
        context.deleteDatabase(dbName)

        val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val firstRepo = RoomPreferenceRepository(firstDb.preferenceDao(), clockMillis = { 1_700_000_000_000L })

        assertEquals(0, firstRepo.observePreferenceCount().first())
        assertNull(firstRepo.getPreference("chave_teste"))

        firstRepo.savePreference("chave_teste", "valor_persistido_etapa1")
        assertEquals(1, firstRepo.observePreferenceCount().first())
        firstDb.close()

        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val reopenedRepo = RoomPreferenceRepository(reopenedDb.preferenceDao())
        val restored = reopenedRepo.getPreference("chave_teste")

        assertNotNull(restored)
        assertEquals("valor_persistido_etapa1", restored?.value)
        assertEquals(1_700_000_000_000L, restored?.updatedAtEpochMs)
        reopenedDb.close()
        assertTrue(context.deleteDatabase(dbName))
    }

    @Test
    fun `startup diagnostic screen displays wallpaper ready title and saves preference`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            // Keep Room's work in sync with the test looper driving Compose.
            .setQueryExecutor { it.run() }
            .build()
        val repo = RoomPreferenceRepository(db.preferenceDao())
        val viewModel = StartupDiagnosticViewModel(repo)

        composeRule.setContent {
            MixVendas3DTheme {
                StartupDiagnosticRoute(viewModel = viewModel)
            }
        }

        composeRule.onNodeWithTag("castle_wallpaper_image").assertExists()
        composeRule.onNodeWithTag("stage1_ready_title")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("MIX VENDAS 3D — Estrutura inicial pronta")

        composeRule.onNodeWithTag("preference_value_input")
            .performScrollTo()
            .performTextInput("Teste Offline OK")
        composeRule.onNodeWithTag("save_preference_button")
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)

        val persistedValue = hasTestTag("persisted_preference_value") and hasText("Teste Offline OK")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(persistedValue).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("persisted_preference_value")
            .performScrollTo()
            .assertTextEquals("Teste Offline OK")

        db.close()
    }
}
