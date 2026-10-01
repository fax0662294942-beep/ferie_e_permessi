package it.feriepermessi.nativeapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import it.feriepermessi.nativeapp.cloud.*
import it.feriepermessi.nativeapp.data.*
import it.feriepermessi.nativeapp.domain.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CandidateRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun pressBack() {
        // This assertion targets dialogs/navigation; an open IME consumes Back first.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
    @Test fun everyScreenReturnsHomeWithoutExiting() {
        var exits = 0
        val user = UserEntity("candidate", name="Profilo di prova")
        compose.setContent { FeriePermessiApp(onExit={exits++}, state=NativeState(users=listOf(user),profile=Profile(user))) }
        for (screen in listOf("Calendario", "Statistiche", "Impostazioni", "Account e cloud")) {
            compose.onNodeWithText(screen).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Home").assertExists()
            pressBack()
            compose.onNodeWithText("Riepilogo").assertExists()
            assertEquals(0, exits)
        }
    }
    @Test fun invalidEditorAndMonthDialogsDismissBeforeExit() {
        var exits = 0
        compose.setContent { FeriePermessiApp(onExit={exits++},state=NativeState(profile=Profile(UserEntity("candidate")))) }
        compose.onNodeWithText("+ Ferie").performScrollTo().performClick()
        compose.onNodeWithText("Data (AAAA-MM-GG)").performTextReplacement("invalid")
        compose.onNodeWithText("Salva").performClick()
        compose.onNodeWithText("Inserisci date valide e una quantità positiva").assertExists()
        pressBack()
        compose.onNodeWithText("Salva").assertDoesNotExist()
        compose.onNodeWithText(YearMonth.now().toString()).performScrollTo().performClick()
        compose.onNodeWithText("AAAA-MM").performTextReplacement("invalid")
        compose.onNodeWithText("Apri").performClick()
        compose.onNodeWithText("Mese non valido").assertExists()
        pressBack()
        compose.onNodeWithText("Seleziona mese").assertDoesNotExist()
        assertEquals(0, exits)
    }
    @Test fun rejectedAccountCannotReachLedgerAndLogoutDialogDismissesFirst() {
        var exits = 0
        val cloud = CloudState(identity=CloudIdentity("test","Prova","test@example.invalid",null),access=PwaCloudContract.Access.Rejected)
        compose.setContent { FeriePermessiApp(onExit={exits++},cloudState=cloud) }
        compose.onNodeWithText("Accesso negato. Contatta l’amministratore.").assertExists()
        compose.onNodeWithText("Riepilogo").assertDoesNotExist()
        compose.onNodeWithText("Esci da Google").performScrollTo().performClick()
        compose.onNodeWithText("Conferma operazione").assertExists()
        pressBack()
        compose.onNodeWithText("Conferma operazione").assertDoesNotExist()
        assertEquals(0, exits)
    }
    @Test fun diskDatabaseSurvivesCloseAndReopenWithoutChangingBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "candidate-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context,NativeDatabase::class.java,name).build()
        var db = open()
        try {
            val repository = NativeRepository(db)
            repository.initialize()
            val id = repository.dao.users().first().id
            repository.saveEntry(EntryEntity(id,"saved","permesso",dateFrom="2026-10-01",dateTo="2026-10-01",quantity=2.5,year=2026,month=10))
            val backup = repository.snapshot()
            db.close()
            db = open()
            val reopened = NativeRepository(db)
            reopened.initialize()
            assertEquals(backup,reopened.snapshot())
            assertEquals(backup,BackupCodec.decode(BackupCodec.encode(reopened.snapshot())))
        } finally { db.close();context.deleteDatabase(name) }
    }
}
