package it.feriepermessi.nativeapp

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import it.feriepermessi.nativeapp.cloud.*
import it.feriepermessi.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FirebaseGatewayTest {
    private fun gateway():Pair<FirebaseApp,FirebaseGateway> {
        val app=FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(),FirebaseOptions.Builder()
            .setProjectId("demo-feriepermessi").setApplicationId("1:123456:android:demo").setApiKey("fake-emulator-key").build(),UUID.randomUUID().toString())
        val auth=FirebaseAuth.getInstance(app);auth.useEmulator("10.0.2.2",9099)
        val store=FirebaseFirestore.getInstance(app)
        store.firestoreSettings=FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        store.useEmulator("10.0.2.2",8080)
        return app to FirebaseGateway(auth,store)
    }
    @Test fun approvalIsolationConflictPreservationRevocationAndLogout()=runBlocking {
        val (adminApp,admin)=gateway();val (userApp,user)=gateway()
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),NativeDatabase::class.java).build()
        try {
            admin.auth.signInAnonymously().await();val aid=requireNotNull(admin.identity())
            assertEquals(PwaCloudContract.Access.Pending,admin.approval(aid).access)
            admin.claimAdmin(aid);assertTrue(admin.approval(aid).admin)
            user.auth.signInAnonymously().await();val uid=requireNotNull(user.identity())
            assertEquals(PwaCloudContract.Access.Pending,user.approval(uid).access)
            try {user.fetch(uid.uid);fail("Pending user accessed data")}catch(_:FirebaseFirestoreException){}
            admin.adminStatus(aid.uid,uid.uid,"approved")
            assertEquals(PwaCloudContract.Access.Approved,user.approval(uid).access)
            val fixture=InstrumentationRegistry.getInstrumentation().context.assets.open("pwa-backup.json").bufferedReader().use {BackupCodec.decode(it.readText())}
            val missing=user.fetch(uid.uid);assertNull(missing.snapshot)
            user.publish(uid.uid,missing,fixture)
            assertEquals(fixture,user.fetch(uid.uid).snapshot)
            val stale=user.fetch(uid.uid)
            // Simulate a PWA extension field and concurrent save.
            admin.firestore.collection(PwaCloudContract.DATA_COLLECTION).document(uid.uid).update(mapOf("extension" to "preserve")).await()
            try {user.publish(uid.uid,stale,fixture);fail("Concurrent edit overwritten")}catch(_:Exception){}
            val latest=user.fetch(uid.uid)
            user.publish(uid.uid,latest,fixture)
            assertEquals("preserve",user.firestore.collection(PwaCloudContract.DATA_COLLECTION).document(uid.uid).get(Source.SERVER).await().getString("extension"))
            try {user.firestore.collection(PwaCloudContract.DATA_COLLECTION).document(aid.uid).get(Source.SERVER).await();fail("Other account accessed")}catch(_:FirebaseFirestoreException){}
            val listenerLatch=CountDownLatch(1)
            val listener=user.watchData(uid.uid) {doc,error -> if(doc?.snapshot!=null && error==null) listenerLatch.countDown()}
            assertTrue(listenerLatch.await(15,TimeUnit.SECONDS));listener.remove()
            admin.adminRole(aid.uid,uid.uid,"admin");assertTrue(user.approval(uid).admin)
            admin.adminRole(aid.uid,uid.uid,"user");assertFalse(user.approval(uid).admin)
            admin.adminStatus(aid.uid,uid.uid,"rejected")
            assertEquals(PwaCloudContract.Access.Rejected,user.approval(uid).access)
            try {user.publish(uid.uid,user.fetch(uid.uid),fixture);fail("Revoked access accepted")}catch(_:Exception){}
            // Loading cloud archives local data atomically; logout cannot erase that database.
            val r=NativeRepository(db);r.initialize();val before=r.snapshot()
            r.restoreCloud(fixture,uid.uid)
            val after=r.snapshot();assertEquals(fixture,after)
            val safety=r.dao.states().single {it.key.startsWith("cloudSafety:")}
            assertEquals(before,BackupCodec.decode(safety.value))
            user.signOut();assertNull(user.identity());assertEquals(after,r.snapshot())
            admin.adminDelete(aid.uid,uid.uid)
            assertFalse(admin.firestore.collection(PwaCloudContract.REGISTRY_COLLECTION).document(uid.uid).get(Source.SERVER).await().exists())
            assertFalse(admin.firestore.collection(PwaCloudContract.DATA_COLLECTION).document(uid.uid).get(Source.SERVER).await().exists())
            admin.signOut()
        }finally {db.close();admin.firestore.terminate().await();user.firestore.terminate().await();adminApp.delete();userApp.delete()}
    }
}
