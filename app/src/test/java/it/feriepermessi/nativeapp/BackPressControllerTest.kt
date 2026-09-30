package it.feriepermessi.nativeapp
import org.junit.Assert.assertEquals
import org.junit.Test
class BackPressControllerTest {
 @Test fun secondBackWithinWindowExits(){var now=1000L;val c=BackPressController({now});assertEquals(BackAction.ShowHint,c.onHomeBack());now=2999;assertEquals(BackAction.Exit,c.onHomeBack())}
 @Test fun expiredWindowRequiresNewHint(){var now=1000L;val c=BackPressController({now});c.onHomeBack();now=3001;assertEquals(BackAction.ShowHint,c.onHomeBack())}
}
