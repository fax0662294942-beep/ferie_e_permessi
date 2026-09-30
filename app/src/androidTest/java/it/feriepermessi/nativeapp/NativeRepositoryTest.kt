package it.feriepermessi.nativeapp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.feriepermessi.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeRepositoryTest {
    @Test fun crudValidationConfirmationAndUserIsolation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NativeDatabase::class.java).build()
        try {
            val r=NativeRepository(db);r.initialize();r.initialize()
            assertEquals(1,r.dao.users().size)
            val id=r.dao.users().first().id
            assertEquals(11,r.profile(id).holidays.size)
            val e=EntryEntity(id,"e","ferie","2026-09-01","2026-09-02",2.0,year=2026,month=9,simulated=true)
            r.saveEntry(e)
            r.saveEntry(e.copy(quantity=3.0,note="modifica"))
            assertEquals(1,r.dao.entries(id).size)
            assertEquals(3.0,r.dao.entries(id).first().quantity,0.0)
            r.confirmMonth(id,2026,8)
            assertTrue(r.dao.entries(id).first().simulated)
            r.confirmMonth(id,2026,9)
            assertFalse(r.dao.entries(id).first().simulated)
            try {r.saveEntry(e.copy(quantity=-1.0));fail("Invalid entry accepted")}catch(_:IllegalArgumentException){}
            val other=r.addUser("Altro")
            assertTrue(r.dao.entries(other).isEmpty())
            r.dao.deleteEntry(id,"e")
            assertTrue(r.dao.entries(id).isEmpty())
            r.deleteUser(other)
            assertEquals(id,r.dao.state("currentUserId"))
            try {r.deleteUser(id);fail("Last user deleted")}catch(_:IllegalArgumentException){}
        }finally{db.close()}
    }
}
