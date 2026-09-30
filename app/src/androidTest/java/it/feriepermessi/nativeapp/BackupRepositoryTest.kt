package it.feriepermessi.nativeapp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.feriepermessi.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    @Test fun transactionalRoundTripAndNonDestructiveLegacyImport() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NativeDatabase::class.java).build()
        try {
            val r=NativeRepository(db);r.initialize()
            val original=r.snapshot()
            val legacy=InstrumentationRegistry.getInstrumentation().context.assets.open("pwa-backup.json").bufferedReader().use {BackupCodec.decode(it.readText())}
            r.restore(legacy)
            val merged=r.snapshot()
            assertEquals(3,merged.users.size);assertTrue(merged.users.containsAll(original.users))
            assertEquals(8,merged.entries.size);assertEquals(1,merged.links.size)
            val imported=merged.users.first {it.name=="Test PWA"}
            assertNotEquals("u",imported.id);assertEquals(imported.id,merged.currentUserId)
            assertEquals(-8.0,imported.initialLeave,0.0)
            val backup=BackupCodec.encode(merged)
            r.addUser("Temporary")
            r.restore(BackupCodec.decode(backup),replace=true)
            assertEquals(merged,r.snapshot())
            try {r.restore(merged.copy(links=listOf(EntryTagEntity(imported.id,"missing","t"))),replace=true);fail("Broken backup accepted")}catch(_:IllegalArgumentException){}
            assertEquals(merged,r.snapshot())
            // Reimport preserves both sets even with identical legacy user/entry IDs.
            r.restore(legacy)
            assertEquals(5,r.dao.users().size);assertEquals(16,r.snapshot().entries.size)
        }finally{db.close()}
    }
}
