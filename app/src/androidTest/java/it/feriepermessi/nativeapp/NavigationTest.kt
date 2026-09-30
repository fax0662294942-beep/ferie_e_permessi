package it.feriepermessi.nativeapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun backReturnsHomeThenRequiresTwoPresses() {
        var hints = 0
        var exits = 0
        var time = 1000L
        compose.setContent { FeriePermessiApp(onHint = { hints++ }, onExit = { exits++ }, back = BackPressController({ time })) }
        compose.onNodeWithText("Calendario").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Riepilogo").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, hints)
        assertEquals(0, exits)
        time += 1999
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, exits)
    }
}
