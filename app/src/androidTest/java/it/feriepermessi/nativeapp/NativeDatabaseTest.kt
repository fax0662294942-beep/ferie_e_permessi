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
class NativeDatabaseTest {
    @Test fun storesAllEntryKindsAndIsolatesUsers() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NativeDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.putUser(UserEntity("a", initialLeave = -5.0, initialTimeBank = 7.5))
            dao.putUser(UserEntity("b"))
            val types = listOf("ferie", "permesso", "permesso_pagato", "banca_accumulo", "banca_fruizione", "permesso_104", "permesso_studio")
            types.forEach { dao.putEntry(EntryEntity("a", it, it, quantity = 2.5, year = 2026, month = 9)) }
            dao.putEntry(EntryEntity("b", "ferie", "ferie", quantity = 9.0, year = 2026, month = 9))
            dao.putTag(TagEntity("a", "t", "Viaggio", "#4ade80"))
            dao.putEntryTag(EntryTagEntity("a", "ferie", "t"))
            assertEquals(7, dao.entries("a").size)
            assertEquals(1, dao.entries("b").size)
            assertEquals(-5.0, dao.users().first().initialLeave, 0.0)
            dao.deleteEntry("a", "ferie")
            assertTrue(dao.entryTags("a").isEmpty())
            assertEquals(1, dao.tags("a").size)
            assertEquals(1, dao.entries("b").size)
        } finally { db.close() }
    }
}
