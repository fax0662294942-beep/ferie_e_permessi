package it.feriepermessi.nativeapp

import android.app.Application
import it.feriepermessi.nativeapp.cloud.CloudController
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.feriepermessi.nativeapp.data.*
import it.feriepermessi.nativeapp.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NativeState(val users: List<UserEntity> = emptyList(), val profile: Profile? = null,
    val tags: List<TagEntity> = emptyList(), val links: List<EntryTagEntity> = emptyList(), val error: String? = null)
class NativeViewModel(application: Application): AndroidViewModel(application) {
    val repository = NativeRepository(NativeDatabase.open(application))
    private val mutable = MutableStateFlow(NativeState())
    val state = mutable.asStateFlow()
    val cloud = CloudController(repository,viewModelScope,reload={reload()})
    init { viewModelScope.launch { repository.initialize();reload();cloud.start() } }
    private suspend fun reload() {
        val users = repository.dao.users()
        val id = repository.dao.state("currentUserId").takeIf { candidate -> users.any { it.id == candidate } } ?: users.first().id
        mutable.value = NativeState(users, repository.profile(id), repository.dao.tags(id), repository.dao.entryTags(id))
    }
    fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { require(cloud.mayEdit) { "Accesso non autorizzato: verifica lo stato dell’account" }; block(); reload(); cloud.localChanged() } catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "Operazione non riuscita") }
    } }
    fun clearError() { mutable.value = mutable.value.copy(error=null) }
    fun selectUser(id: String) = action { repository.dao.putState(AppStateEntity("currentUserId",id)) }
    fun addUser(name: String) = action { repository.addUser(name) }
    fun saveUser(user: UserEntity, year: YearConfigEntity) = action { repository.database.withTransaction {
        repository.dao.putUser(user); repository.dao.putYear(year)
    } }
    fun saveEntry(entry: EntryEntity, tags: List<String>) = action { repository.saveEntry(entry,tags) }
    fun deleteEntry(entry: EntryEntity) = action { repository.dao.deleteEntry(entry.userId,entry.id) }
    fun confirm(entry: EntryEntity) = action { repository.dao.putEntry(entry.copy(simulated=false)) }
    fun confirmMonth(id: String,year: Int,month: Int) = action { repository.confirmMonth(id,year,month) }
    fun putTag(tag: TagEntity) = action { repository.dao.putTag(tag) }
    fun deleteTag(tag: TagEntity) = action { repository.dao.deleteTag(tag.userId,tag.id) }
    fun putHoliday(holiday: HolidayEntity) = action { repository.dao.putHoliday(holiday) }
    fun deleteHoliday(holiday: HolidayEntity) = action { repository.dao.deleteHoliday(holiday.userId,holiday.id) }
    override fun onCleared() { cloud.close(); repository.database.close() }
}
