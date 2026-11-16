package pl.ekim.fm1_flasher

import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.*

class LocalCalendarDayTest {
    @Test fun localMidnightAndDstUseCalendarDateRatherThanElapsed24Hours() {
        val previous = TimeZone.getDefault()
        try {
            val zone = ZoneId.of("Europe/Warsaw")
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (date in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                assertNotEquals(86400000L, end - start)
                assertEquals(date.toEpochDay() - 1, localCalendarDay(start - 1))
                assertEquals(date.toEpochDay(), localCalendarDay(start))
                assertEquals(date.toEpochDay(), localCalendarDay(end - 1))
                assertEquals(date.toEpochDay() + 1, localCalendarDay(end))
            }
        } finally { TimeZone.setDefault(previous) }
    }
}
