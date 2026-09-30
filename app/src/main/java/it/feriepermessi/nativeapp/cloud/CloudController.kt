package it.feriepermessi.nativeapp.cloud

import android.content.Context
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import it.feriepermessi.nativeapp.R
import it.feriepermessi.nativeapp.data.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("LongParameterList")
data class CloudState(val identity:CloudIdentity?=null,val access:PwaCloudContract.Access=PwaCloudContract.Access.Approved,
    val admin:Boolean=false,val mayClaim:Boolean=false,val busy:Boolean=false,val linked:Boolean=false,
    val remote:CloudDocument?=null,val registry:List<RegistryUser> = emptyList(),val message:String?=null)

class CloudController(private val repository:NativeRepository,private val scope:CoroutineScope,
    private val reload:suspend ()->Unit,private val gateway:FirebaseGateway=FirebaseGateway()) {
    private val mutable=MutableStateFlow(CloudState())
    val state=mutable.asStateFlow()
    private val mutex=Mutex()
    private var authListener:FirebaseAuth.AuthStateListener?=null
    private var listeners=emptyList<ListenerRegistration>()
    private var dataListener:ListenerRegistration?=null
    private var sessionUid:String?=null
    private var remoteBaseline:CloudDocument?=null
    private var localBaseline:String?=null
    private var syncJob:Job?=null
    private var edits=0L
    val mayEdit get()=mutable.value.identity==null || mutable.value.access==PwaCloudContract.Access.Approved
    fun start() {
        val listener=FirebaseAuth.AuthStateListener {
            val identity=gateway.identity()
            if(identity?.uid!=sessionUid || mutable.value.identity==null) {
                listeners.forEach {it.remove()};listeners=emptyList();dataListener?.remove();dataListener=null;syncJob?.cancel()
                sessionUid=identity?.uid;remoteBaseline=null;localBaseline=null
                mutable.value=CloudState(identity=identity,access=if(identity==null) PwaCloudContract.Access.Approved else PwaCloudContract.Access.Pending)
                if(identity!=null) {
                    listeners=gateway.watchApproval(identity.uid) {refreshApproval(identity)}
                    refreshApproval(identity)
                }
            }
        }
        authListener=listener;gateway.auth.addAuthStateListener(listener)
    }
    private fun localFingerprint(snapshot:NativeSnapshot)=CloudPayload.fingerprint(Json.parseToJsonElement(PwaCloudContract.encode(snapshot)).jsonObject)
    private fun current(uid:String)=sessionUid==uid && gateway.identity()?.uid==uid
    private fun launchOperation(block:suspend ()->Unit) {scope.launch {
        mutex.withLock {
            mutable.value=mutable.value.copy(busy=true,message=null)
            try {block()}catch(e:CancellationException){throw e}
            catch(e:Exception){mutable.value=mutable.value.copy(message=e.message ?: "Operazione cloud non riuscita")}
            finally{mutable.value=mutable.value.copy(busy=false)}
        }
    }}
    private fun refreshApproval(identity:CloudIdentity) = launchOperation {
        if(!current(identity.uid)) return@launchOperation
        try {
            val approval=gateway.approval(identity)
            if(!current(identity.uid)) return@launchOperation
            mutable.value=mutable.value.copy(access=approval.access,admin=approval.admin,mayClaim=approval.mayClaim)
            if(approval.access!=PwaCloudContract.Access.Approved) {
                remoteBaseline=null;localBaseline=null;dataListener?.remove();dataListener=null
                mutable.value=mutable.value.copy(linked=false,remote=null,registry=emptyList())
            } else if(remoteBaseline==null) {
                val remote=gateway.fetch(identity.uid)
                if(!current(identity.uid)) return@launchOperation
                remoteBaseline=remote;mutable.value=mutable.value.copy(remote=remote)
                dataListener?.remove()
                dataListener=gateway.watchData(identity.uid) {eventDocument,error ->
                    scope.launch {mutex.withLock {
                        if(!current(identity.uid) || mutable.value.access!=PwaCloudContract.Access.Approved) return@withLock
                        if(error!=null) {mutable.value=mutable.value.copy(message="Cloud non disponibile: ${error.message}");return@withLock}
                        if(eventDocument==null || eventDocument.fingerprint==remoteBaseline?.fingerprint) return@withLock
                        try {
                        // A queued listener event may predate our last transaction. Always re-read the latest revision.
                        val document=gateway.fetch(identity.uid)
                        if(document.fingerprint==remoteBaseline?.fingerprint || !current(identity.uid)) return@withLock
                        val unchanged=localFingerprint(repository.snapshot())==localBaseline
                        if(mutable.value.linked && unchanged && document.snapshot!=null) {
                            repository.restoreCloud(document.snapshot,identity.uid);reload()
                            localBaseline=localFingerprint(repository.snapshot())
                        }else {
                            mutable.value=mutable.value.copy(linked=false,message="Il cloud è cambiato: confronta i dati prima di continuare.")
                        }
                        remoteBaseline=document;mutable.value=mutable.value.copy(remote=document)
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){if(current(identity.uid)) mutable.value=mutable.value.copy(linked=false,message="Aggiornamento cloud non riuscito: ${e.message}")}
                    }}
                }
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception){if(current(identity.uid)) {
            remoteBaseline=null;localBaseline=null
            mutable.value=mutable.value.copy(access=PwaCloudContract.Access.Error,linked=false,admin=false,remote=null,message="Verifica accesso non riuscita: ${e.message}")
        }}
    }
    fun refresh() {
        val identity=gateway.identity() ?: return
        // Remove old data listeners before another server refresh.
        listeners.forEach {it.remove()};dataListener?.remove();dataListener=null;listeners=gateway.watchApproval(identity.uid) {refreshApproval(identity)}
        remoteBaseline=null;localBaseline=null
        mutable.value=mutable.value.copy(linked=false,remote=null)
        refreshApproval(identity)
    }
    suspend fun mutate(block:suspend ()->Unit)=mutex.withLock {
        require(mayEdit) {"Accesso non autorizzato: verifica lo stato dell’account"}
        block()
    }
    suspend fun login(context:Context) {
        val option=GetGoogleIdOption.Builder().setServerClientId(context.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
        val response=CredentialManager.create(context).getCredential(context,GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential=response.credential
        require(credential is CustomCredential && credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        gateway.signIn(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    }
    suspend fun logout(context:Context) {
        gateway.signOut()
        // The auth listener resets the session immediately. Room data and recovery snapshots remain.
        runCatching {CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())}
    }
    fun claimAdmin()=launchOperation {
        val identity=requireNotNull(gateway.identity());gateway.claimAdmin(identity)
        refreshApproval(identity)
    }
    fun useCloud()=launchOperation {
        val identity=requireNotNull(gateway.identity());require(mutable.value.access==PwaCloudContract.Access.Approved)
        val remote=gateway.fetch(identity.uid)
        require(remote.fingerprint==remoteBaseline?.fingerprint) {"Il cloud è cambiato: aggiorna il confronto"}
        val snapshot=requireNotNull(remote.snapshot) {"Nessun dato cloud da caricare"}
        if(!current(identity.uid)) return@launchOperation
        repository.restoreCloud(snapshot,identity.uid);reload()
        remoteBaseline=remote;localBaseline=localFingerprint(repository.snapshot())
        mutable.value=mutable.value.copy(linked=true,remote=remote,message="Dati cloud caricati. Copia di sicurezza locale conservata.")
    }
    fun useLocal()=launchOperation {
        val identity=requireNotNull(gateway.identity());require(mutable.value.access==PwaCloudContract.Access.Approved)
        val expected=requireNotNull(remoteBaseline) {"Aggiorna il confronto"}
        // Explicit UI confirmation establishes which account owns this local ledger.
        val snapshot=repository.snapshot();gateway.publish(identity.uid,expected,snapshot)
        if(!current(identity.uid)) return@launchOperation
        repository.dao.putState(AppStateEntity("cloudBoundUid",identity.uid))
        localBaseline=localFingerprint(snapshot);remoteBaseline=gateway.fetch(identity.uid)
        mutable.value=mutable.value.copy(linked=true,remote=remoteBaseline,message="Sincronizzazione attiva")
    }
    fun localChanged() {
        if(!mutable.value.linked) return
        edits++
        if(syncJob?.isActive==true) return
        syncJob=scope.launch {
            while(isActive && mutable.value.linked) {
                delay(800);val revision=edits
                synchronize()
                if(revision==edits) break
            }
        }
    }
    fun syncNow()=scope.launch {synchronize()}
    private suspend fun synchronize()=mutex.withLock {
        val identity=gateway.identity() ?: return@withLock
        if(!mutable.value.linked || mutable.value.access!=PwaCloudContract.Access.Approved) return@withLock
        mutable.value=mutable.value.copy(busy=true)
        try {
            require(repository.dao.state("cloudBoundUid")==identity.uid) {"Collega prima questo account ai dati locali"}
            val snapshot=repository.snapshot()
            gateway.publish(identity.uid,requireNotNull(remoteBaseline),snapshot)
            if(!current(identity.uid)) return@withLock
            localBaseline=localFingerprint(snapshot);remoteBaseline=gateway.fetch(identity.uid)
            mutable.value=mutable.value.copy(remote=remoteBaseline,message="Sincronizzato")
        }catch(e:CancellationException){throw e}
        catch(e:Exception){if(current(identity.uid)) mutable.value=mutable.value.copy(linked=false,message=e.message ?: "Sincronizzazione non riuscita: aggiorna il confronto")}
        finally{mutable.value=mutable.value.copy(busy=false)}
    }
    fun loadRegistry()=launchOperation {val id=requireNotNull(gateway.identity()).uid
        val users=gateway.listRegistry(id);if(current(id)) mutable.value=mutable.value.copy(registry=users)}
    fun setStatus(user:RegistryUser,status:String)=launchOperation {gateway.adminStatus(requireNotNull(gateway.identity()).uid,user.uid,status);loadRegistry()}
    fun setRole(user:RegistryUser,role:String)=launchOperation {gateway.adminRole(requireNotNull(gateway.identity()).uid,user.uid,role);loadRegistry()}
    fun deleteUser(user:RegistryUser)=launchOperation {gateway.adminDelete(requireNotNull(gateway.identity()).uid,user.uid);loadRegistry()}
    fun close() {dataListener?.remove();listeners.forEach {it.remove()};authListener?.let {gateway.auth.removeAuthStateListener(it)};syncJob?.cancel()}
}
