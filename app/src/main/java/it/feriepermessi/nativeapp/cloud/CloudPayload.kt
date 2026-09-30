package it.feriepermessi.nativeapp.cloud

import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Retain extension fields belonging to records that still exist; local deletions remain deletions. */
object CloudPayload {
    fun merge(previous:JsonObject, current:JsonObject):JsonObject = mergeElement(previous,current).jsonObject
    private fun mergeElement(old:JsonElement?, fresh:JsonElement):JsonElement = when(fresh) {
        is JsonObject -> JsonObject((old as? JsonObject).orEmpty().toMutableMap().apply {
            fresh.forEach {(key,value) -> this[key]=mergeElement(this[key],value)}
        })
        is JsonArray -> {
            val prior=(old as? JsonArray).orEmpty()
            JsonArray(fresh.map { item ->
                val id=(item as? JsonObject)?.get("id")
                if(id==null) item else mergeElement(prior.firstOrNull {(it as? JsonObject)?.get("id")==id},item)
            })
        }
        else -> fresh
    }
    fun mergeValues(previous:Map<String,Any?>,current:Map<String,Any?>):Map<String,Any?> {
        fun combine(old:Any?,fresh:Any?):Any? = when(fresh) {
            is Map<*,*> -> (old as? Map<*,*>).orEmpty().entries.associate {it.key.toString() to it.value}.toMutableMap().apply {
                fresh.forEach {(key,value) -> this[key.toString()]=combine(this[key.toString()],value)}
            }
            is List<*> -> fresh.map {item ->
                val id=(item as? Map<*,*>)?.get("id")
                if(id==null) item else combine((old as? List<*>).orEmpty().firstOrNull {(it as? Map<*,*>)?.get("id")==id},item)
            }
            else -> fresh
        }
        return previous.toMutableMap().apply {current.forEach {(key,value) -> this[key]=combine(this[key],value)}}
    }
    fun fingerprint(document:JsonObject?):String {
        if(document==null) return "absent"
        fun canonical(element:JsonElement):JsonElement = when(element) {
            is JsonObject -> JsonObject(element.toSortedMap().mapValues {canonical(it.value)})
            is JsonArray -> JsonArray(element.map {canonical(it)})
            else -> element
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical(document).toString().toByteArray(Charsets.UTF_8))
            .joinToString("") {"%02x".format(it)}
    }
}
