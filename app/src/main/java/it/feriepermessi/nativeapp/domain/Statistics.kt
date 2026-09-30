package it.feriepermessi.nativeapp.domain

import it.feriepermessi.nativeapp.data.EntryEntity
import java.time.LocalDate
import java.time.YearMonth

data class Breakdown(val obligatoryEpisodes: Int, val requestedEpisodes: Int,
    val obligatoryQuantity: Double, val requestedQuantity: Double)
object Statistics {
    fun entries(p: Profile, period: String, month: YearMonth, from: LocalDate? = null, to: LocalDate? = null): List<EntryEntity> = p.entries.filter { e ->
        !e.simulated && when(period) {
            "month" -> e.year == month.year && e.month == month.monthValue
            "year" -> e.year == month.year
            "custom" -> from != null && to != null && e.dateFrom != null && LocalDate.parse(e.dateFrom) in from..to
            else -> true
        }
    }
    fun breakdown(p: Profile, entries: List<EntryEntity>, type: String): Breakdown {
        val selected = entries.filter { it.type == type }
        val factor = if(type == "ferie") p.user.calcMode else 1.0
        return Breakdown(selected.count { it.obligatory }, selected.count { !it.obligatory },
            cents(selected.filter { it.obligatory }.sumOf { it.quantity * factor }),
            cents(selected.filter { !it.obligatory }.sumOf { it.quantity * factor }))
    }
    fun days(p: Profile, e: EntryEntity): List<LocalDate> {
        val from = e.dateFrom?.let(LocalDate::parse) ?: e.dateTo?.let(LocalDate::parse) ?: return emptyList()
        if(e.type == "permesso") return listOf(from)
        val to = e.dateTo?.let(LocalDate::parse) ?: from
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.filter { WorkCalendar.isWorkday(p,it) }.toList()
    }
    fun weekdays(p: Profile, entries: List<EntryEntity>, type: String = "tutti", category: String = "tutte"): List<Int> {
        val counts = MutableList(7) { 0 }
        entries.filter { (type == "tutti" || it.type == type) && when(category) { "obbligate" -> it.obligatory; "richieste" -> !it.obligatory; else -> true } }
            .forEach { e -> days(p,e).forEach { counts[it.dayOfWeek.value-1]++ } }
        return counts
    }
    fun accrued(p: Profile, engine: LeaveEngine, period: String, month: YearMonth, from: LocalDate? = null, to: LocalDate? = null, effectiveYear: Int = LocalDate.now().year): Amounts {
        if(period == "month") return engine.monthAccrued(p,month.year,month.monthValue)
        if(period == "year") return engine.annualAccrued(p,month.year)
        val values = mutableListOf<Amounts>()
        if(period == "custom") {
            if(from == null || to == null || to < from) return Amounts()
            var cursor = YearMonth.from(from)
            while(cursor <= YearMonth.from(to)) { values.add(engine.monthAccrued(p,cursor.year,cursor.monthValue)); cursor=cursor.plusMonths(1) }
        } else {
            val start = p.user.initialDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).year }
                ?: p.user.contractStart?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).year }
                ?: (p.years.map { it.year } + month.year).min()
            for(year in start..effectiveYear) values.add(engine.annualAccrued(p,year))
        }
        return Amounts(cents(values.sumOf { it.vacation }),cents(values.sumOf { it.leave }))
    }
    // Calendar preserves the PWA's assignment to the entry's starting month.
    fun dayEntries(p: Profile, month: YearMonth, day: LocalDate, simulated: Boolean): List<EntryEntity> = p.entries.filter { e ->
        e.year == month.year && e.month == month.monthValue && (simulated || !e.simulated) &&
            e.dateFrom != null && day >= LocalDate.parse(e.dateFrom) && day <= LocalDate.parse(e.dateTo ?: e.dateFrom)
    }
}
