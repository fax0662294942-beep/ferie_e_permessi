package it.feriepermessi.nativeapp.domain

import it.feriepermessi.nativeapp.data.*
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.*

data class Profile(val user: UserEntity, val years: List<YearConfigEntity> = emptyList(),
    val entries: List<EntryEntity> = emptyList(), val holidays: List<HolidayEntity> = emptyList()) {
    fun config(year: Int) = years.find { it.year == year } ?: YearConfigEntity(user.id, year)
}
data class Amounts(val vacation: Double = 0.0, val leave: Double = 0.0)
data class Usage(val vacationRaw: Double, val vacation: Double, val enjoyed: Double, val paid: Double) {
    val leave get() = cents(enjoyed + paid)
}
data class MonthBalance(val start: Amounts, val accrued: Amounts, val used: Usage, val end: Amounts)
data class AnnualTotals(val previous: Amounts, val previousUsed: Amounts, val previousRemaining: Amounts,
    val accrued: Amounts, val currentUsed: Amounts, val currentRemaining: Amounts,
    val previousEnjoyed: Double, val previousPaid: Double, val currentEnjoyed: Double, val currentPaid: Double)
internal fun cents(value: Double): Double = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

// Faithful to main:index.html. The injected date makes all time-sensitive rules testable.
class LeaveEngine(private val today: LocalDate, private val simulationMonth: YearMonth? = null) {
    private val effective = simulationMonth ?: YearMonth.from(today)
    fun monthlyRate(annual: Double, month: Int): Double {
        require(month in 1..12)
        if (annual == 0.0) return 0.0
        val floorCents = floor(annual / 12 * 100).toInt()
        val monthsAtFloor = 12 - (floor(annual * 100 + 0.5).toInt() - floorCents * 12)
        return (floorCents + if (month <= monthsAtFloor) 0 else 1) / 100.0
    }
    fun monthAccrues(p: Profile, year: Int, month: Int): Boolean {
        val target = YearMonth.of(year, month)
        var startDay = 1
        for (raw in listOf(p.user.contractStart, p.user.initialDate)) {
            if (raw.isNullOrBlank()) continue
            val date = LocalDate.parse(raw)
            if (YearMonth.from(date) > target) return false
            if (YearMonth.from(date) == target) startDay = max(startDay, date.dayOfMonth)
        }
        if (startDay > 15 || target > effective) return false
        return target != effective || simulationMonth != null || today.dayOfMonth >= 15
    }
    fun leaveRate(p: Profile, year: Int, month: Int): Double {
        val u = p.user
        if (u.calcMode != 1.2 || u.contractStart.isNullOrBlank()) return monthlyRate(p.config(year).leaveAnnual, month)
        val start = LocalDate.parse(u.contractStart)
        val seniority = (year - start.year) * 12 + month - start.monthValue
        if (seniority < 0) return 0.0
        val annual = when { seniority < 24 -> u.ccnl02.takeUnless { it == 0.0 } ?: 32.0
            seniority < 48 -> u.ccnl24.takeUnless { it == 0.0 } ?: 68.0
            else -> u.ccnl5plus.takeUnless { it == 0.0 } ?: 104.0 }
        return monthlyRate(annual, month)
    }
    fun monthAccrued(p: Profile, year: Int, month: Int): Amounts =
        if (!monthAccrues(p, year, month)) Amounts() else Amounts(monthlyRate(p.config(year).vacationAnnual, month), leaveRate(p, year, month))
    fun annualAccrued(p: Profile, year: Int): Amounts {
        val months = (1..12).map { monthAccrued(p, year, it) }
        return Amounts(cents(months.sumOf { it.vacation }), cents(months.sumOf { it.leave }))
    }
    private fun afterInitial(p: Profile, e: EntryEntity): Boolean = p.user.initialDate.isNullOrBlank() ||
        e.dateFrom.isNullOrBlank() || e.dateFrom >= p.user.initialDate!!
    private fun usage(p: Profile, entries: List<EntryEntity>): Usage {
        val vacation = entries.filter { it.type == "ferie" }.sumOf { it.quantity }
        return Usage(cents(vacation), cents(vacation * p.user.calcMode),
            cents(entries.filter { it.type == "permesso" }.sumOf { it.quantity }),
            cents(entries.filter { it.type == "permesso_pagato" }.sumOf { it.quantity }))
    }
    fun yearUsed(p: Profile, year: Int, simulated: Boolean = false): Usage = usage(p, p.entries.filter {
        it.year == year && (simulated || !it.simulated) && afterInitial(p, it) &&
            (simulationMonth == null || YearMonth.of(it.year, it.month) <= effective)
    })
    fun monthUsed(p: Profile, year: Int, month: Int, simulated: Boolean = false): Usage = usage(p, p.entries.filter {
        it.year == year && it.month == month && (simulated || !it.simulated) && afterInitial(p, it)
    })
    fun previous(p: Profile, year: Int): Amounts {
        val u = p.user
        var balance = Amounts()
        val initial = u.initialDate?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        val start: Int
        if (initial != null) {
            balance = Amounts(u.initialVacation, u.initialLeave)
            if (year <= initial.year) return balance
            start = initial.year
        } else {
            val candidates = p.years.filter { it.year < year }.map { it.year }.toMutableList()
            u.contractStart?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).year }?.takeIf { it < year }?.let(candidates::add)
            start = candidates.minOrNull() ?: year
        }
        for (y in start until year) {
            val mat = annualAccrued(p, y)
            val used = yearUsed(p, y)
            balance = Amounts(max(0.0, cents(balance.vacation + mat.vacation - used.vacation)), cents(balance.leave + mat.leave - used.leave))
        }
        return balance
    }
    fun annualTotals(p: Profile, year: Int, simulated: Boolean = false): AnnualTotals {
        val mat = annualAccrued(p, year)
        val used = yearUsed(p, year, simulated)
        val prev = previous(p, year)
        val consumed = Amounts(cents(min(used.vacation, prev.vacation)), cents(min(used.leave, max(0.0, prev.leave))))
        val current = Amounts(cents(used.vacation - consumed.vacation), cents(used.leave - consumed.leave))
        val enjoyed = cents(min(used.enjoyed, consumed.leave))
        val paid = cents(max(0.0, consumed.leave - used.enjoyed))
        return AnnualTotals(prev, consumed, Amounts(cents(prev.vacation - consumed.vacation), cents(prev.leave - consumed.leave)), mat, current,
            Amounts(cents(mat.vacation - current.vacation), cents(mat.leave - current.leave)), enjoyed, paid,
            cents(max(0.0, used.enjoyed - enjoyed)), cents(max(0.0, used.paid - paid)))
    }
    fun monthBalance(p: Profile, year: Int, month: Int, simulated: Boolean = false): MonthBalance {
        var balance = previous(p, year)
        for (m in 1 until month) {
            val mat = monthAccrued(p, year, m); val used = monthUsed(p, year, m, simulated)
            balance = Amounts(cents(balance.vacation + mat.vacation - used.vacation), cents(balance.leave + mat.leave - used.leave))
        }
        val mat = monthAccrued(p, year, month); val used = monthUsed(p, year, month, simulated)
        return MonthBalance(balance, mat, used, Amounts(cents(balance.vacation + mat.vacation - used.vacation), cents(balance.leave + mat.leave - used.leave)))
    }
    fun timeBank(p: Profile, year: Int, month: Int): Double = cents(p.user.initialTimeBank + p.entries.filter {
        !it.simulated && afterInitial(p, it) && YearMonth.of(it.year, it.month) <= YearMonth.of(year, month)
    }.sumOf { when(it.type) { "banca_accumulo" -> it.quantity; "banca_fruizione" -> -it.quantity; else -> 0.0 } })
    fun specialAnnual(p: Profile, type: String, year: Int): Double = cents(p.entries.filter {
        it.type == type && !it.simulated && it.year == year && afterInitial(p, it)
    }.sumOf { it.quantity })
    // Legacy monthly special-leave counters intentionally do not filter the initial date.
    fun specialMonth(p: Profile, type: String, year: Int, month: Int): Double = cents(p.entries.filter {
        it.type == type && !it.simulated && it.year == year && it.month == month
    }.sumOf { it.quantity })
    fun law104AnnualBudget(p: Profile) = (p.user.law104MonthlyBudget.takeUnless { it == 0.0 } ?: 24.0) * 12
}

object WorkCalendar {
    fun easterMonday(year: Int): LocalDate {
        val a = year % 19; val b = year / 100; val c = year % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        return LocalDate.of(year, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1).plusDays(1)
    }
    fun defaultHolidays(userId: String): List<HolidayEntity> {
        val dates = listOf(1 to 1, 1 to 6, 4 to 25, 5 to 1, 6 to 2, 8 to 15, 11 to 1, 12 to 8, 12 to 25, 12 to 26)
        val names = listOf("Capodanno", "Epifania", "Festa della Liberazione", "Festa dei Lavoratori", "Festa della Repubblica", "Ferragosto", "Ognissanti", "Immacolata Concezione", "Natale", "Santo Stefano")
        return dates.mapIndexed { index, (month, day) -> HolidayEntity(userId, "holiday-$index", names[index], month, day) } +
            HolidayEntity(userId, "easter", "Pasquetta", easterMonday = true)
    }
    fun isHoliday(p: Profile, date: LocalDate): Boolean = p.holidays.any { h ->
        val matches = if (h.easterMonday) date == easterMonday(date.year) else h.month == date.monthValue && h.day == date.dayOfMonth
        matches && if (h.recurring) h.fromYear == null || date.year >= h.fromYear else h.year == date.year
    }
    fun isWorkday(p: Profile, date: LocalDate): Boolean =
        !(p.user.excludeSaturday && date.dayOfWeek.value == 6) && !(p.user.excludeSunday && date.dayOfWeek.value == 7) && !isHoliday(p, date)
    fun workdays(p: Profile, from: LocalDate, to: LocalDate): Int {
        require(to >= from)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.count { isWorkday(p, it) }
    }
}
