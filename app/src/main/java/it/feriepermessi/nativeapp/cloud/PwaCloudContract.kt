package it.feriepermessi.nativeapp.cloud

import it.feriepermessi.nativeapp.data.*
import kotlinx.serialization.json.*

/** Exact collection and field names read directly from main:index.html. No network side effects. */
object PwaCloudContract {
    const val DATA_COLLECTION="feriePermessi_data"
    const val REGISTRY_COLLECTION="feriePermessi_registry"
    const val CONFIG_COLLECTION="feriePermessi_config"
    const val ADMINS_DOCUMENT="admins"
    enum class Access { Approved, Pending, Rejected, Error }
    fun access(uid:String, admins:Set<String>?, registryStatus:String?, readSucceeded:Boolean):Access {
        if(!readSucceeded || uid.isBlank() || admins==null) return Access.Error
        if(uid in admins) return Access.Approved
        return when(registryStatus) {"approved"->Access.Approved;"rejected"->Access.Rejected;else->Access.Pending}
    }
    fun isAdmin(uid:String,admins:Set<String>?)=uid.isNotBlank() && admins?.contains(uid)==true
    fun encode(snapshot:NativeSnapshot):String {
        BackupCodec.validate(snapshot)
        return buildJsonObject {
            put("currentUserId",snapshot.currentUserId)
            put("users",buildJsonArray {snapshot.users.forEach {u -> add(buildJsonObject {
                put("id",u.id);put("name",u.name);put("calcMode",u.calcMode.toString())
                put("dataInizioContratto",u.contractStart?.let(::JsonPrimitive) ?: JsonNull)
                put("permessiCCNL",buildJsonObject {put("anni02",u.ccnl02);put("anni24",u.ccnl24);put("anni5plus",u.ccnl5plus)})
                put("workdayConfig",buildJsonObject {put("escludiSabato",u.excludeSaturday);put("escludiDomenica",u.excludeSunday)})
                put("saldoIniziale",buildJsonObject {
                    put("data",u.initialDate?.let(::JsonPrimitive) ?: JsonNull)
                    put("ferie",u.initialVacation);put("permessi",u.initialLeave);put("bancaOre",u.initialTimeBank)
                })
                put("bancaOreEnabled",u.timeBankEnabled);put("permesso104Enabled",u.law104Enabled);put("permessoStudioEnabled",u.studyEnabled)
                put("permesso104Budget",u.law104MonthlyBudget);put("permessoStudioBudget",u.studyAnnualBudget)
                put("anni",buildJsonObject {snapshot.years.filter {it.userId==u.id}.forEach {y ->
                    put(y.year.toString(),buildJsonObject {put("ferieAnnue",y.vacationAnnual);put("permessiOreAnnui",y.leaveAnnual)}) }})
                put("entries",buildJsonArray {snapshot.entries.filter {it.userId==u.id}.forEach {e -> add(buildJsonObject {
                    put("id",e.id);put("tipo",e.type);put("qty",e.quantity);put("note",e.note);put("anno",e.year);put("mese",e.month)
                    put("dateFrom",e.dateFrom?.let(::JsonPrimitive) ?: JsonNull);put("dateTo",e.dateTo?.let(::JsonPrimitive) ?: JsonNull)
                    put("sim",e.simulated);put("obbligata",e.obligatory)
                    put("tags",buildJsonArray {snapshot.links.filter {it.userId==u.id && it.entryId==e.id}.forEach {add(JsonPrimitive(it.tagId))}})
                })}})
                put("tags",buildJsonArray {snapshot.tags.filter {it.userId==u.id}.forEach {t -> add(buildJsonObject {
                    put("id",t.id);put("name",t.name);put("color",t.color)
                })}})
                put("festivita",buildJsonArray {snapshot.holidays.filter {it.userId==u.id}.forEach {h -> add(buildJsonObject {
                    put("id",h.id);put("name",h.name);put("month",h.month?.let(::JsonPrimitive) ?: JsonNull)
                    put("day",h.day?.let(::JsonPrimitive) ?: JsonNull);put("easter",h.easterMonday);put("ricorrente",h.recurring)
                    put("fromYear",h.fromYear?.let(::JsonPrimitive) ?: JsonNull);put("year",h.year?.let(::JsonPrimitive) ?: JsonNull)
                })}})
            })}})
        }.toString()
    }
}
