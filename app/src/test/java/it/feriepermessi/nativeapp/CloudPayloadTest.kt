package it.feriepermessi.nativeapp

import it.feriepermessi.nativeapp.cloud.CloudPayload
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CloudPayloadTest {
    private fun obj(s:String)=Json.parseToJsonElement(s).jsonObject
    @Test fun retainsExtensionFieldsAndHonorsRecordDeletion() {
        val old=obj("""{"serverOnly":7,"users":[{"id":"u","extension":"keep","permessiCCNL":{"custom":9,"anni02":32},"entries":[{"id":"e","future":true,"qty":1},{"id":"deleted","qty":2}]}]}""")
        val new=obj("""{"users":[{"id":"u","permessiCCNL":{"anni02":40},"entries":[{"id":"e","qty":3}]}]}""")
        val merged=CloudPayload.merge(old,new)
        assertEquals(7,merged.getValue("serverOnly").jsonPrimitive.int)
        val u=merged.getValue("users").jsonArray.first().jsonObject
        assertEquals("keep",u.getValue("extension").jsonPrimitive.content)
        assertEquals(9,u.getValue("permessiCCNL").jsonObject.getValue("custom").jsonPrimitive.int)
        assertEquals(1,u.getValue("entries").jsonArray.size)
        assertTrue(u.getValue("entries").jsonArray.first().jsonObject.getValue("future").jsonPrimitive.boolean)
    }
    @Test fun fingerprintIgnoresMapOrderButDetectsContentAndTimestampChanges() {
        assertEquals(CloudPayload.fingerprint(obj("""{"b":2,"a":{"x":1,"y":2}}""")),CloudPayload.fingerprint(obj("""{"a":{"y":2,"x":1},"b":2}""")))
        assertNotEquals(CloudPayload.fingerprint(null),CloudPayload.fingerprint(obj("{}")))
        assertNotEquals(CloudPayload.fingerprint(obj("""{"lastModified":1}""")),CloudPayload.fingerprint(obj("""{"lastModified":2}""")))
    }
    @Test fun preservesOpaqueFirestoreExtensionTypes() {
        val opaque=Any()
        val old=mapOf<String,Any?>("timestampExtension" to opaque,"users" to listOf(mapOf("id" to "u","typedExtension" to opaque,"name" to "Old")))
        val fresh=mapOf<String,Any?>("users" to listOf(mapOf("id" to "u","name" to "New")))
        val merged=CloudPayload.mergeValues(old,fresh)
        assertSame(opaque,merged["timestampExtension"])
        val user=(merged["users"] as List<*>).first() as Map<*,*>
        assertSame(opaque,user["typedExtension"])
        assertEquals("New",user["name"])
    }
}
