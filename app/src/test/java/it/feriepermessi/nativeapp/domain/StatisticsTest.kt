package it.feriepermessi.nativeapp.domain
import it.feriepermessi.nativeapp.data.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test
class StatisticsTest {
    private val e=EntryEntity("a","e","ferie","2026-04-06","2026-04-12",4.0,year=2026,month=4,obligatory=true)
    private val p=Profile(UserEntity("a",calcMode=1.2),entries=listOf(e,e.copy(id="sim",simulated=true)),holidays=WorkCalendar.defaultHolidays("a"))
    @Test fun periodsExcludeSimulationAndWeekdaysExcludeHolidays() {
        val entries=Statistics.entries(p,"month",YearMonth.of(2026,4))
        assertEquals(1,entries.size)
        assertEquals(Breakdown(1,0,4.8,0.0),Statistics.breakdown(p,entries,"ferie"))
        assertEquals(listOf(0,1,1,1,1,0,0),Statistics.weekdays(p,entries))
        assertTrue(Statistics.entries(p,"custom",YearMonth.of(2026,4),LocalDate.of(2026,4,7),LocalDate.of(2026,4,30)).isEmpty())
    }
    @Test fun calendarKeepsOriginalStartingMonthAssignmentAndIncludesRangeWeekends() {
        assertEquals(1,Statistics.dayEntries(p,YearMonth.of(2026,4),LocalDate.of(2026,4,12),false).size)
        assertEquals(2,Statistics.dayEntries(p,YearMonth.of(2026,4),LocalDate.of(2026,4,12),true).size)
        assertTrue(Statistics.dayEntries(p,YearMonth.of(2026,5),LocalDate.of(2026,4,12),true).isEmpty())
    }
}
