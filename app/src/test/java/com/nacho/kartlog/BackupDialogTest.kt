package com.nacho.kartlog

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.backup.BackupInfo
import com.nacho.kartlog.data.backup.BackupService
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.util.SessionsCsvExporter
import com.nacho.kartlog.ui.importing.FakeSettings
import com.nacho.kartlog.ui.settings.SettingsScreen
import com.nacho.kartlog.ui.settings.SettingsViewModel
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream

/** La confirmación de restaurar enseña qué se va a sustituir y solo al aceptarla se restaura y reinicia (B5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class BackupDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private var vm: SettingsViewModel? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
    }

    @After
    fun tearDown() {
        vm?.viewModelScope?.cancel()
        awaitRoomClosed()
        db.close()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @Test
    fun theDialogWarnsThatEverythingIsReplaced_andConfirmingRestoresAndRestarts() {
        val info = BackupInfo(4, 1_760_000_000_000, 3, "0.1.0")
        var restored = 0
        var restarts = 0
        val fake = object : BackupService {
            override suspend fun export() = File("x.zip")
            override suspend fun inspect(open: () -> InputStream?) = Result.success(info)
            override suspend fun restore(open: () -> InputStream?): Result<BackupInfo> { restored++; return Result.success(info) }
        }
        val model = SettingsViewModel(
            null, SessionsCsvExporter(SessionRepositoryImpl(db.sessionDao()), LapRepositoryImpl(db.lapDao())),
            FakeSettings(""), backup = fake, restartApp = { restarts++ },
        )
        vm = model
        model.prepareRestore { null }
        compose.setContent { KartLogTheme { SettingsScreen(viewModel = model) } }

        compose.waitUntil(8_000) { compose.onAllNodesWithText("¿Restaurar esta copia?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("restore_message").assertIsDisplayed()
        check(compose.onAllNodesWithText("SUSTITUIRÁN todos los datos", substring = true).fetchSemanticsNodes().isNotEmpty()) { "debe avisar de que se sustituye todo" }
        assertEquals("nada cambia hasta confirmar", 0, restored)

        compose.onNodeWithTag("restore_confirm").performClick()
        compose.waitUntil(8_000) { restarts == 1 }
        assertEquals(1, restored)
    }

    @Test
    fun cancellingTheDialog_changesNothing() {
        val info = BackupInfo(4, 1_760_000_000_000, 0, "0.1.0")
        var restored = 0
        val fake = object : BackupService {
            override suspend fun export() = File("x.zip")
            override suspend fun inspect(open: () -> InputStream?) = Result.success(info)
            override suspend fun restore(open: () -> InputStream?): Result<BackupInfo> { restored++; return Result.success(info) }
        }
        val model = SettingsViewModel(
            null, SessionsCsvExporter(SessionRepositoryImpl(db.sessionDao()), LapRepositoryImpl(db.lapDao())),
            FakeSettings(""), backup = fake,
        )
        vm = model
        model.prepareRestore { null }
        compose.setContent { KartLogTheme { SettingsScreen(viewModel = model) } }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("¿Restaurar esta copia?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("CANCELAR").performClick()
        compose.waitForIdle()
        check(compose.onAllNodesWithText("¿Restaurar esta copia?").fetchSemanticsNodes().isEmpty())
        assertEquals(0, restored)
    }
}
