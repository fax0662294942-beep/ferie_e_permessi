package it.feriepermessi.nativeapp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NativeDao {
    @Query("SELECT * FROM users ORDER BY rowid") fun observeUsers(): Flow<List<UserEntity>>
    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteUser(id: String)
    @Query("DELETE FROM users") suspend fun clearUsers()
    @Query("SELECT * FROM app_state") suspend fun states(): List<AppStateEntity>
    @Query("DELETE FROM app_state") suspend fun clearState()
    @Query("SELECT * FROM users ORDER BY rowid") suspend fun users(): List<UserEntity>
    @Upsert suspend fun putUser(user: UserEntity)
    @Query("SELECT * FROM year_config WHERE userId = :userId") suspend fun years(userId: String): List<YearConfigEntity>
    @Upsert suspend fun putYear(year: YearConfigEntity)
    @Query("SELECT * FROM entries WHERE userId = :userId ORDER BY dateFrom, id") fun observeEntries(userId: String): Flow<List<EntryEntity>>
    @Query("SELECT * FROM entries WHERE userId = :userId ORDER BY dateFrom, id") suspend fun entries(userId: String): List<EntryEntity>
    @Upsert suspend fun putEntry(entry: EntryEntity)
    @Query("DELETE FROM entries WHERE userId = :userId AND id = :id") suspend fun deleteEntry(userId: String, id: String)
    @Query("SELECT * FROM tags WHERE userId = :userId") suspend fun tags(userId: String): List<TagEntity>
    @Upsert suspend fun putTag(tag: TagEntity)
    @Query("DELETE FROM tags WHERE userId = :userId AND id = :id") suspend fun deleteTag(userId: String, id: String)
    @Query("DELETE FROM entry_tags WHERE userId = :userId AND entryId = :entryId") suspend fun clearEntryTags(userId: String, entryId: String)
    @Query("DELETE FROM holidays WHERE userId = :userId AND id = :id") suspend fun deleteHoliday(userId: String, id: String)
    @Upsert suspend fun putEntryTag(link: EntryTagEntity)
    @Query("SELECT * FROM entry_tags WHERE userId = :userId") suspend fun entryTags(userId: String): List<EntryTagEntity>
    @Query("SELECT * FROM holidays WHERE userId = :userId") suspend fun holidays(userId: String): List<HolidayEntity>
    @Upsert suspend fun putHoliday(holiday: HolidayEntity)
    @Upsert suspend fun putState(state: AppStateEntity)
    @Query("SELECT value FROM app_state WHERE `key` = :key") suspend fun state(key: String): String?
}

@Database(entities = [UserEntity::class, YearConfigEntity::class, EntryEntity::class,
    TagEntity::class, EntryTagEntity::class, HolidayEntity::class, AppStateEntity::class], version = 1, exportSchema = true)
abstract class NativeDatabase : RoomDatabase() {
    abstract fun dao(): NativeDao
    companion object {
        fun open(context: Context): NativeDatabase = Room.databaseBuilder(
            context.applicationContext, NativeDatabase::class.java, "ferie-permessi-native.db"
        ).build() // No destructive fallback: future versions must supply explicit migrations.
    }
}
