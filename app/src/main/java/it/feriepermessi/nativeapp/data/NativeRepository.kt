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
    suspend fun snapshot(): NativeSnapshot = database.withTransaction {
        val users=dao.users()
        NativeSnapshot(users,users.flatMap {dao.years(it.id)},users.flatMap {dao.entries(it.id)},
            users.flatMap {dao.tags(it.id)},users.flatMap {dao.entryTags(it.id)},users.flatMap {dao.holidays(it.id)},
            dao.state("currentUserId") ?: users.first().id)
    }
    suspend fun restore(snapshot: NativeSnapshot, replace: Boolean = false) {
        BackupCodec.validate(snapshot) // Complete validation before any mutation.
        database.withTransaction {
            val remap=snapshot.users.associate {it.id to if(replace) it.id else UUID.randomUUID().toString()}
            if(replace) {dao.clearUsers();dao.clearState()}
            snapshot.users.forEach {dao.putUser(it.copy(id=remap.getValue(it.id)))}
            snapshot.years.forEach {dao.putYear(it.copy(userId=remap.getValue(it.userId)))}
            snapshot.entries.forEach {dao.putEntry(it.copy(userId=remap.getValue(it.userId)))}
            snapshot.tags.forEach {dao.putTag(it.copy(userId=remap.getValue(it.userId)))}
            snapshot.links.forEach {dao.putEntryTag(it.copy(userId=remap.getValue(it.userId)))}
            snapshot.holidays.forEach {dao.putHoliday(it.copy(userId=remap.getValue(it.userId)))}
            dao.putState(AppStateEntity("currentUserId",remap.getValue(snapshot.currentUserId)))
        }
    }
    suspend fun restoreCloud(snapshot: NativeSnapshot, accountUid: String) = database.withTransaction {
        require(accountUid.isNotBlank())
        val previous=BackupCodec.encode(this.snapshot())
        val archived=dao.states().filter {it.key.startsWith("cloudSafety:")}
        restore(snapshot,replace=true)
        archived.forEach {dao.putState(it)}
        dao.putState(AppStateEntity("cloudSafety:${System.currentTimeMillis()}:${UUID.randomUUID()}",previous))
        dao.putState(AppStateEntity("cloudBoundUid",accountUid))
    }
}
