package it.feriepermessi.nativeapp.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import it.feriepermessi.nativeapp.domain.WorkCalendar
import java.time.LocalDate
import java.time.MonthDay

@Serializable
data class NativeSnapshot(
    val users: List<UserEntity>, val years: List<YearConfigEntity> = emptyList(),
    val entries: List<EntryEntity> = emptyList(), val tags: List<TagEntity> = emptyList(),
    val links: List<EntryTagEntity> = emptyList(), val holidays: List<HolidayEntity> = emptyList(),
    val currentUserId: String
)
@Serializable private data class BackupEnvelope(
    val format: String = "ferie-permessi-native", val version: Int = 1, val data: NativeSnapshot
)

object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val types = setOf("ferie","permesso","permesso_pagato","banca_accumulo","banca_fruizione","permesso_104","permesso_studio")
    fun encode(data: NativeSnapshot): String { validate(data); return json.encodeToString(BackupEnvelope(data=data)) }
    fun decode(text: String): NativeSnapshot {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup troppo grande" }
        val root = json.parseToJsonElement(text).jsonObject
        val data = if (root.containsKey("format") || root.containsKey("version")) {
            require(root["format"]?.jsonPrimitive?.content == "ferie-permessi-native") { "Formato non supportato" }
            require(root["version"]?.jsonPrimitive?.intOrNull == 1) { "Versione backup non supportata" }
            json.decodeFromJsonElement<BackupEnvelope>(root).data
        } else legacy(root)
        validate(data)
        return data
    }
    private fun legacy(root: JsonObject): NativeSnapshot {
        val users=mutableListOf<UserEntity>();val years=mutableListOf<YearConfigEntity>()
        val entries=mutableListOf<EntryEntity>();val tags=mutableListOf<TagEntity>()
        val links=mutableListOf<EntryTagEntity>();val holidays=mutableListOf<HolidayEntity>()
        root.getValue("users").jsonArray.forEach { value ->
            val u=value.jsonObject;val id=u.required("id")
            val c=u.obj("permessiCCNL");val w=u.obj("workdayConfig");val si=u.obj("saldoIniziale")
            users += UserEntity(id,u.string("name","Utente"),u.number("calcMode",1.0),u.optional("dataInizioContratto"),
                c.number("anni02",32.0),c.number("anni24",68.0),c.number("anni5plus",104.0),
                w.bool("escludiSabato",true),w.bool("escludiDomenica",true),si.optional("data"),
                si.number("ferie",0.0),si.number("permessi",0.0),si.number("bancaOre",0.0),
                u.bool("bancaOreEnabled"),u.bool("permesso104Enabled"),u.bool("permessoStudioEnabled"),
                u.number("permesso104Budget",24.0),u.number("permessoStudioBudget",150.0))
            u.obj("anni").forEach { (year, config) -> val cfg=config.jsonObject
                years += YearConfigEntity(id,year.toInt(),cfg.number("ferieAnnue",26.0),cfg.number("permessiOreAnnui",100.0)) }
            u.array("tags").forEach { val t=it.jsonObject;tags += TagEntity(id,t.required("id"),t.required("name"),t.string("color","#4ade80")) }
            u.array("entries").forEach { val e=it.jsonObject;val eid=e.required("id")
                entries += EntryEntity(id,eid,e.required("tipo"),e.optional("dateFrom"),e.optional("dateTo"),
                    e.getValue("qty").jsonPrimitive.double,e.string("note",""),e.getValue("anno").jsonPrimitive.int,
                    e.getValue("mese").jsonPrimitive.int,e.bool("sim"),e.bool("obbligata"))
                e.array("tags").forEach { tag -> links += EntryTagEntity(id,eid,tag.jsonPrimitive.content) }
            }
            if (!u.containsKey("festivita")) holidays += WorkCalendar.defaultHolidays(id)
            else u.array("festivita").forEach { val h=it.jsonObject
                holidays += HolidayEntity(id,h.required("id"),h.required("name"),h.integer("month"),h.integer("day"),
                    h.bool("easter"),h.bool("ricorrente",true),h.integer("fromYear"),h.integer("year")) }
        }
        return NativeSnapshot(users,years,entries,tags,links,holidays,root.optional("currentUserId") ?: users.first().id)
    }
    fun validate(s: NativeSnapshot) {
        require(s.users.isNotEmpty()) { "Il backup deve contenere almeno un utente" }
        require(s.users.map { it.id }.distinct().size == s.users.size) { "ID utente duplicati" }
        val ids=s.users.map { it.id }.toSet();require(s.currentUserId in ids)
        fun numbers(vararg n: Double) { require(n.all { it.isFinite() }) { "Numero non valido" } }
        fun date(value: String?) { value?.let { LocalDate.parse(it) } }
        s.users.forEach { u ->
            require(u.id.isNotBlank() && u.name.isNotBlank());require(u.calcMode==1.0 || u.calcMode==1.2)
            date(u.contractStart);date(u.initialDate)
            numbers(u.ccnl02,u.ccnl24,u.ccnl5plus,u.initialVacation,u.initialLeave,u.initialTimeBank,u.law104MonthlyBudget,u.studyAnnualBudget)
            require(u.ccnl02>=0 && u.ccnl24>=0 && u.ccnl5plus>=0 && u.law104MonthlyBudget>0 && u.studyAnnualBudget>0)
        }
        fun <T> owned(items:List<T>, owner:(T)->String, key:(T)->Any) {
            require(items.all {owner(it) in ids});require(items.map(key).distinct().size==items.size) { "Identità duplicata" }
        }
        owned(s.years,{it.userId},{it.userId to it.year});owned(s.entries,{it.userId},{it.userId to it.id})
        owned(s.tags,{it.userId},{it.userId to it.id});owned(s.holidays,{it.userId},{it.userId to it.id})
        owned(s.links,{it.userId},{Triple(it.userId,it.entryId,it.tagId)})
        s.years.forEach {require(it.year in 1900..9999);numbers(it.vacationAnnual,it.leaveAnnual);require(it.vacationAnnual>=0 && it.leaveAnnual>=0)}
        s.entries.forEach { e ->
            require(e.id.isNotBlank() && e.type in types && e.year in 1900..9999 && e.month in 1..12)
            numbers(e.quantity);require(e.quantity>0)
            date(e.dateFrom);date(e.dateTo)
            e.dateFrom?.let { from -> val d=LocalDate.parse(from);require(d.year==e.year && d.monthValue==e.month)
                require(e.dateTo==null || LocalDate.parse(e.dateTo)>=d) }
            require(e.dateTo==null || e.dateFrom!=null)
        }
        s.tags.forEach {require(it.id.isNotBlank() && it.name.isNotBlank())}
        s.holidays.forEach {h -> require(h.id.isNotBlank() && h.name.isNotBlank())
            if(!h.easterMonday) MonthDay.of(requireNotNull(h.month),requireNotNull(h.day))
            if(!h.recurring) require(h.year != null && h.year in 1900..9999)
            h.fromYear?.let {require(it in 1900..9999)} }
        val entryIds=s.entries.map {it.userId to it.id}.toSet();val tagIds=s.tags.map {it.userId to it.id}.toSet()
        require(s.links.all { (it.userId to it.entryId) in entryIds && (it.userId to it.tagId) in tagIds }) { "Tag o movimento mancante" }
    }
    private fun JsonObject.obj(key:String)=this[key]?.takeUnless {it==JsonNull}?.jsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.array(key:String)=this[key]?.takeUnless {it==JsonNull}?.jsonArray ?: JsonArray(emptyList())
    private fun JsonObject.optional(key:String)=this[key]?.takeUnless {it==JsonNull}?.jsonPrimitive?.content?.ifBlank {null}
    private fun JsonObject.required(key:String)=requireNotNull(optional(key)) { "Campo mancante: $key" }
    private fun JsonObject.string(key:String,default:String)=optional(key) ?: default
    private fun JsonObject.number(key:String,default:Double)=this[key]?.takeUnless {it==JsonNull}?.jsonPrimitive?.double ?: default
    private fun JsonObject.integer(key:String)=this[key]?.takeUnless {it==JsonNull}?.jsonPrimitive?.int
    private fun JsonObject.bool(key:String,default:Boolean=false)=this[key]?.takeUnless {it==JsonNull}?.jsonPrimitive?.boolean ?: default
}
