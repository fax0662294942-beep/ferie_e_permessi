package it.feriepermessi.nativeapp.data

import androidx.room.withTransaction
import it.feriepermessi.nativeapp.domain.*
import java.time.LocalDate
import java.util.UUID

class NativeRepository(val database: NativeDatabase) {
    val dao = database.dao()
    suspend fun initialize() = database.withTransaction {
        if (dao.users().isEmpty()) addUser("Utente")
    }
    suspend fun addUser(name: String): String {
        require(name.isNotBlank())
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            dao.putUser(UserEntity(id, name.trim()))
            val year = LocalDate.now().year
            dao.putYear(YearConfigEntity(id,year-1)); dao.putYear(YearConfigEntity(id,year))
            WorkCalendar.defaultHolidays(id).forEach { dao.putHoliday(it) }
            dao.putState(AppStateEntity("currentUserId",id))
        }
        return id
    }
    suspend fun profile(id: String): Profile {
        val user = dao.users().first { it.id == id }
        return Profile(user, dao.years(id), dao.entries(id), dao.holidays(id))
    }
    suspend fun saveEntry(entry: EntryEntity, tags: List<String> = emptyList()) {
        require(entry.quantity.isFinite() && entry.quantity > 0)
        require(entry.type in setOf("ferie","permesso","permesso_pagato","banca_accumulo","banca_fruizione","permesso_104","permesso_studio"))
        require(entry.month in 1..12)
        val from = LocalDate.parse(requireNotNull(entry.dateFrom))
        val to = LocalDate.parse(entry.dateTo ?: entry.dateFrom)
        require(to >= from)
        require(from.year == entry.year && from.monthValue == entry.month)
        database.withTransaction {
            dao.putEntry(entry)
            dao.clearEntryTags(entry.userId,entry.id)
            tags.distinct().forEach { dao.putEntryTag(EntryTagEntity(entry.userId,entry.id,it)) }
        }
    }
    suspend fun confirmMonth(userId: String, year: Int, month: Int) = database.withTransaction {
        dao.entries(userId).filter { it.year == year && it.month == month && it.simulated }.forEach { dao.putEntry(it.copy(simulated=false)) }
    }
    suspend fun deleteUser(id: String) = database.withTransaction {
        require(dao.users().size > 1) { "Deve rimanere almeno un utente" }
        dao.deleteUser(id)
        if (dao.state("currentUserId") == id) dao.putState(AppStateEntity("currentUserId",dao.users().first().id))
    }
}
