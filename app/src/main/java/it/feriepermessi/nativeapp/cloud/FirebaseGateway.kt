package it.feriepermessi.nativeapp.cloud

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.*
import it.feriepermessi.nativeapp.data.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.*

data class CloudIdentity(val uid:String,val name:String,val email:String,val photoUrl:String?)
data class Approval(val access:PwaCloudContract.Access,val admin:Boolean,val mayClaim:Boolean)
data class CloudDocument(val raw:JsonObject?,val snapshot:NativeSnapshot?) {
    val fingerprint=CloudPayload.fingerprint(raw)
}
data class RegistryUser(val uid:String,val name:String,val email:String,val status:String,val role:String)
class CloudConflict:IllegalStateException("I dati cloud sono cambiati. Ricarica il confronto prima di sincronizzare.")

class FirebaseGateway(val auth:FirebaseAuth=FirebaseAuth.getInstance(),val firestore:FirebaseFirestore=FirebaseFirestore.getInstance()) {
    fun identity()=auth.currentUser?.let {CloudIdentity(it.uid,it.displayName ?: "Utente",it.email.orEmpty(),it.photoUrl?.toString())}
    private fun ensureIdentity(uid:String) {require(auth.currentUser?.uid==uid) {"Account cambiato: ripeti l’operazione"}}
    private fun adminsRef()=firestore.collection(PwaCloudContract.CONFIG_COLLECTION).document(PwaCloudContract.ADMINS_DOCUMENT)
    private fun registry(uid:String)=firestore.collection(PwaCloudContract.REGISTRY_COLLECTION).document(uid)
    private fun data(uid:String)=firestore.collection(PwaCloudContract.DATA_COLLECTION).document(uid)
    suspend fun signIn(idToken:String) {auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken,null)).await()}
    fun signOut()=auth.signOut()
    suspend fun approval(identity:CloudIdentity):Approval {
        ensureIdentity(identity.uid)
        val adminDoc=adminsRef().get(Source.SERVER).await()
        val admins=(adminDoc.get("uids") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
        val reg=registry(identity.uid).get(Source.SERVER).await()
        if(!reg.exists()) registry(identity.uid).set(mapOf(
            "name" to identity.name,"email" to identity.email,"photoURL" to identity.photoUrl,
            "status" to if(identity.uid in admins) "approved" else "pending",
            "role" to if(identity.uid in admins) "admin" else "user","registeredAt" to FieldValue.serverTimestamp()
        )).await()
        ensureIdentity(identity.uid)
        return Approval(PwaCloudContract.access(identity.uid,admins,reg.getString("status"),true),identity.uid in admins,admins.isEmpty())
    }
    fun watchApproval(uid:String,changed:()->Unit):List<ListenerRegistration> = listOf(
        adminsRef().addSnapshotListener {_,_ -> changed()},registry(uid).addSnapshotListener {_,_ -> changed()})
    fun watchData(uid:String,changed:(CloudDocument?,Exception?)->Unit):ListenerRegistration = data(uid).addSnapshotListener {snap,error ->
        if(error!=null) changed(null,error)
        else if(snap!=null && !snap.metadata.hasPendingWrites() && !snap.metadata.isFromCache) {
            try {changed(document(snap),null)}catch(e:Exception){changed(null,e)}
        }
    }
    suspend fun fetch(uid:String):CloudDocument {
        ensureIdentity(uid)
        val result=document(data(uid).get(Source.SERVER).await())
        ensureIdentity(uid);return result
    }
    private fun document(snap:DocumentSnapshot):CloudDocument {
        val raw=if(snap.exists()) element(snap.data).jsonObject else null
        val decoded=raw?.let {BackupCodec.decode(it.toString())}
        return CloudDocument(raw,decoded)
    }
    private fun approved(transaction:Transaction,uid:String,adminOnly:Boolean=false):Set<String> {
        ensureIdentity(uid)
        val adminDoc=transaction.get(adminsRef())
        val admins=(adminDoc.get("uids") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
        val reg=transaction.get(registry(uid))
        require(if(adminOnly) uid in admins else uid in admins || reg.getString("status")=="approved") {"Accesso non autorizzato"}
        return admins
    }
    suspend fun publish(uid:String,expected:CloudDocument,snapshot:NativeSnapshot) {
        val fresh=Json.parseToJsonElement(PwaCloudContract.encode(snapshot)).jsonObject
        firestore.runTransaction {tx ->
            approved(tx,uid)
            val latest=tx.get(data(uid))
            val raw=if(latest.exists()) element(latest.data).jsonObject else null
            if(CloudPayload.fingerprint(raw)!=expected.fingerprint) throw CloudConflict()
            val merged=CloudPayload.merge(raw ?: JsonObject(emptyMap()),fresh)
            // Firestore's document limit is 1 MiB. Leave room for server timestamps/index overhead.
            require(merged.toString().toByteArray(Charsets.UTF_8).size<900_000) {"Dati troppo grandi per il documento cloud"}
            val fields=fresh.mapValues {toValue(it.value)}
            // Retain native Timestamp, GeoPoint, Blob and DocumentReference values in unknown fields.
            val update=CloudPayload.mergeValues(latest.data.orEmpty(),fields).toMutableMap()
            update["lastModified"]=FieldValue.serverTimestamp()
            ensureIdentity(uid);tx.set(data(uid),update,SetOptions.merge());null
        }.await()
        ensureIdentity(uid)
    }
    suspend fun claimAdmin(identity:CloudIdentity) {
        firestore.runTransaction {tx ->
            ensureIdentity(identity.uid)
            val doc=tx.get(adminsRef());val ids=(doc.get("uids") as? List<*>) ?: emptyList<Any>()
            require(ids.isEmpty()) {"Un amministratore è già configurato"}
            tx.set(adminsRef(),mapOf("uids" to listOf(identity.uid),"updatedAt" to FieldValue.serverTimestamp()),SetOptions.merge())
            tx.set(registry(identity.uid),mapOf("name" to identity.name,"email" to identity.email,"photoURL" to identity.photoUrl,
                "status" to "approved","role" to "admin","updatedAt" to FieldValue.serverTimestamp()),SetOptions.merge());null
        }.await()
    }
    suspend fun listRegistry(uid:String):List<RegistryUser> {
        ensureIdentity(uid);require(approval(requireNotNull(identity())).admin)
        return firestore.collection(PwaCloudContract.REGISTRY_COLLECTION).get(Source.SERVER).await().documents.map {
            RegistryUser(it.id,it.getString("name").orEmpty(),it.getString("email").orEmpty(),it.getString("status") ?: "pending",it.getString("role") ?: "user")
        }.sortedBy {when(it.status){"pending"->0;"approved"->1;else->2}}
    }
    suspend fun adminStatus(uid:String,target:String,status:String) {
        require(uid!=target && status in setOf("pending","approved","rejected"))
        firestore.runTransaction {tx ->approved(tx,uid,true)
            tx.update(registry(target),mapOf("status" to status,"updatedAt" to FieldValue.serverTimestamp()));null}.await()
    }
    suspend fun adminRole(uid:String,target:String,role:String) {
        require(uid!=target && role in setOf("admin","user"))
        firestore.runTransaction {tx ->
            val admins=approved(tx,uid,true).toMutableSet()
            require(tx.get(registry(target)).exists())
            if(role=="admin") admins.add(target) else admins.remove(target)
            require(admins.isNotEmpty())
            tx.update(registry(target),mapOf("role" to role,"updatedAt" to FieldValue.serverTimestamp()))
            tx.set(adminsRef(),mapOf("uids" to admins.toList(),"updatedAt" to FieldValue.serverTimestamp()),SetOptions.merge());null
        }.await()
    }
    suspend fun adminDelete(uid:String,target:String) {
        require(uid!=target)
        firestore.runTransaction {tx ->
            val admins=approved(tx,uid,true)-target
            require(admins.isNotEmpty())
            tx.delete(data(target));tx.delete(registry(target))
            tx.set(adminsRef(),mapOf("uids" to admins.toList(),"updatedAt" to FieldValue.serverTimestamp()),SetOptions.merge());null
        }.await()
    }
    companion object {
        private fun element(value:Any?):JsonElement = when(value) {
            null->JsonNull
            is Map<*,*>->JsonObject(value.entries.associate {it.key.toString() to element(it.value)})
            is List<*>->JsonArray(value.map(::element))
            is Boolean->JsonPrimitive(value)
            is Number->JsonPrimitive(value)
            is Blob->JsonPrimitive(java.util.Base64.getEncoder().encodeToString(value.toBytes()))
            is GeoPoint->buildJsonObject {put("latitude",value.latitude);put("longitude",value.longitude)}
            is DocumentReference->JsonPrimitive(value.path)
            is Timestamp->buildJsonObject {put("seconds",value.seconds);put("nanoseconds",value.nanoseconds)}
            else->JsonPrimitive(value.toString())
        }
        private fun toValue(value:JsonElement):Any? = when(value) {
            JsonNull->null
            is JsonObject->value.mapValues {toValue(it.value)}
            is JsonArray->value.map(::toValue)
            is JsonPrimitive->if(value.isString) value.content else value.booleanOrNull ?: value.longOrNull ?: value.double
        }
    }
}
