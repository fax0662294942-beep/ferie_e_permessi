package it.feriepermessi.nativeapp

import it.feriepermessi.nativeapp.cloud.PwaCloudContract as C
import it.feriepermessi.nativeapp.data.BackupCodec
import org.junit.Assert.*
import org.junit.Test

class PwaCloudContractTest {
    @Test fun cloudPayloadRoundTripsAllPwaFields() {
        val s=BackupCodec.decode(javaClass.getResource("/pwa-backup.json")!!.readText())
        assertEquals(s,BackupCodec.decode(C.encode(s)))
        assertEquals("feriePermessi_data",C.DATA_COLLECTION)
        assertEquals("feriePermessi_registry",C.REGISTRY_COLLECTION)
        assertEquals("feriePermessi_config",C.CONFIG_COLLECTION)
    }
    @Test fun approvalAndAdminPolicyFailsClosedOnReadErrors() {
        assertEquals(C.Access.Approved,C.access("u",setOf("u"),"pending",true))
        assertEquals(C.Access.Approved,C.access("u",emptySet(),"approved",true))
        assertEquals(C.Access.Rejected,C.access("u",emptySet(),"rejected",true))
        assertEquals(C.Access.Pending,C.access("u",emptySet(),null,true))
        assertEquals(C.Access.Error,C.access("u",setOf("u"),"approved",false))
        assertEquals(C.Access.Error,C.access("u",null,"approved",true))
        assertFalse(C.isAdmin("u",null));assertFalse(C.isAdmin("u",emptySet()));assertTrue(C.isAdmin("u",setOf("u")))
    }
}
