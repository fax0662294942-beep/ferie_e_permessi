package it.feriepermessi.nativeapp.data

import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Serializable
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String = "Utente",
    val calcMode: Double = 1.0,
    val contractStart: String? = null,
    val ccnl02: Double = 32.0,
    val ccnl24: Double = 68.0,
    val ccnl5plus: Double = 104.0,
    val excludeSaturday: Boolean = true,
    val excludeSunday: Boolean = true,
    val initialDate: String? = null,
    val initialVacation: Double = 0.0,
    val initialLeave: Double = 0.0,
    val initialTimeBank: Double = 0.0,
    val timeBankEnabled: Boolean = false,
    val law104Enabled: Boolean = false,
    val studyEnabled: Boolean = false,
    val law104MonthlyBudget: Double = 24.0,
    val studyAnnualBudget: Double = 150.0
)

@Serializable
@Entity(tableName = "year_config", primaryKeys = ["userId", "year"],
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)], indices = [Index("userId")])
data class YearConfigEntity(val userId: String, val year: Int, val vacationAnnual: Double = 26.0, val leaveAnnual: Double = 100.0)

// Composite identities preserve legacy IDs even when distinct users share an ID.
@Serializable
@Entity(tableName = "entries", primaryKeys = ["userId", "id"],
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)], indices = [Index("userId")])
data class EntryEntity(
    val userId: String, val id: String, val type: String,
    val dateFrom: String? = null, val dateTo: String? = null,
    val quantity: Double, val note: String = "", val year: Int, val month: Int,
    val simulated: Boolean = false, val obligatory: Boolean = false
)

@Serializable
@Entity(tableName = "tags", primaryKeys = ["userId", "id"],
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)], indices = [Index("userId")])
data class TagEntity(val userId: String, val id: String, val name: String, val color: String)

@Serializable
@Entity(tableName = "entry_tags", primaryKeys = ["userId", "entryId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = EntryEntity::class, parentColumns = ["userId", "id"], childColumns = ["userId", "entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["userId", "id"], childColumns = ["userId", "tagId"], onDelete = ForeignKey.CASCADE)
    ], indices = [Index(value = ["userId", "entryId"]), Index(value = ["userId", "tagId"])])
data class EntryTagEntity(val userId: String, val entryId: String, val tagId: String)

@Serializable
@Entity(tableName = "holidays", primaryKeys = ["userId", "id"],
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)], indices = [Index("userId")])
data class HolidayEntity(val userId: String, val id: String, val name: String,
    val month: Int? = null, val day: Int? = null, val easterMonday: Boolean = false,
    val recurring: Boolean = true, val fromYear: Int? = null, val year: Int? = null)

@Serializable
@Entity(tableName = "app_state")
data class AppStateEntity(@PrimaryKey val key: String, val value: String)
