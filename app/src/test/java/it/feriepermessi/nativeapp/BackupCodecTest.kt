package it.feriepermessi.nativeapp

import it.feriepermessi.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private fun fixture()=javaClass.getResource("/pwa-backup.json")!!.readText()
    private fun invalid(text:String) {
        try {BackupCodec.decode(text);fail("Invalid backup accepted")} catch(_:IllegalArgumentException){}
    }
    @Test fun legacyMappingAndNativeRoundTrip() {
        val s=BackupCodec.decode(fixture())
        assertEquals(2,s.users.size);assertEquals(8,s.entries.size)
        val u=s.users.first()
        assertEquals("2024-09-11",u.contractStart);assertEquals(-8.0,u.initialLeave,0.0)
        assertEquals(1.2,u.calcMode,0.0);assertFalse(u.excludeSaturday)
        assertTrue(u.timeBankEnabled && u.law104Enabled && u.studyEnabled)
        assertEquals(2,s.years.size);assertEquals(1,s.links.size)
        assertEquals(13,s.holidays.size) // Missing old holiday config receives PWA defaults.
        assertTrue(s.entries.first().simulated && s.entries.first().obligatory)
        val encoded=BackupCodec.encode(s)
        assertEquals(s,BackupCodec.decode(encoded))
        assertEquals(s,BackupCodec.decode(encoded.replace("\"version\": 1","\"version\": 1, \"futureField\": true")))
        invalid(encoded.replace("\"version\": 1","\"version\": 2"))
    }
    @Test fun rejectsBrokenIdentityReferencesAndData() {
        invalid("{}");invalid("{\"users\":[]}")
        invalid(fixture().replace("\"currentUserId\":\"u\"","\"currentUserId\":\"missing\""))
        invalid(fixture().replace("\"id\":\"v\"","\"id\":\"u\""))
        invalid(fixture().replace("\"tags\":[\"t\"]","\"tags\":[\"missing\"]"))
        invalid(fixture().replace("\"qty\":2.4","\"qty\":-2.4"))
        invalid(fixture().replace("\"tipo\":\"ferie\"","\"tipo\":\"unknown\""))
        invalid(fixture().replace("2026-09-02","2026-09-00"))
        invalid(fixture().replace("\"mese\":9","\"mese\":13"))
        invalid(fixture().replace("\"calcMode\":\"1.2\"","\"calcMode\":\"NaN\""))
    }
}
