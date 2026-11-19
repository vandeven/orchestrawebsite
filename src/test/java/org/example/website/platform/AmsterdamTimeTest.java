package org.example.website.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.example.website.platform.api.AmsterdamTime;
import org.junit.jupiter.api.Test;

/** Plan: Language, dates and time zone. Amsterdam time, including the days the clocks change. */
class AmsterdamTimeTest {

    @Test
    void theFormatCannotBeMisread() {
        assertEquals(
                "Sunday 13 December 2026, 20:00",
                AmsterdamTime.dateTime(Instant.parse("2026-12-13T19:00:00Z")));
        assertEquals("13 December 2026", AmsterdamTime.date(Instant.parse("2026-12-13T19:00:00Z")));
    }

    @Test
    void winterTimeIsOneHourAheadOfUtc() {
        assertEquals(
                "Thursday 15 January 2026, 13:30",
                AmsterdamTime.dateTime(Instant.parse("2026-01-15T12:30:00Z")));
    }

    @Test
    void summerTimeIsTwoHoursAheadOfUtc() {
        assertEquals(
                "Wednesday 15 July 2026, 14:30",
                AmsterdamTime.dateTime(Instant.parse("2026-07-15T12:30:00Z")));
    }

    @Test
    void theNightTheClocksGoForwardSkipsAnHour() {
        // 29 March 2026: at 01:00 UTC (02:00 winter time) the clocks jump to 03:00
        assertEquals(
                "Sunday 29 March 2026, 01:59",
                AmsterdamTime.dateTime(Instant.parse("2026-03-29T00:59:00Z")));
        assertEquals(
                "Sunday 29 March 2026, 03:00",
                AmsterdamTime.dateTime(Instant.parse("2026-03-29T01:00:00Z")));
    }

    @Test
    void theNightTheClocksGoBackShowsAnHourTwice() {
        // 25 October 2026: at 01:00 UTC (03:00 summer time) the clocks go back to 02:00
        assertEquals(
                "Sunday 25 October 2026, 02:30",
                AmsterdamTime.dateTime(Instant.parse("2026-10-25T00:30:00Z")));
        assertEquals(
                "Sunday 25 October 2026, 02:30",
                AmsterdamTime.dateTime(Instant.parse("2026-10-25T01:30:00Z")));
        assertEquals(
                "Sunday 25 October 2026, 03:00",
                AmsterdamTime.dateTime(Instant.parse("2026-10-25T02:00:00Z")));
    }

    @Test
    void closeToMidnightTheDateIsTheDateInAmsterdam() {
        // 23:30 UTC on New Year's Eve is already New Year in Amsterdam
        assertEquals(
                "Friday 1 January 2027, 00:30",
                AmsterdamTime.dateTime(Instant.parse("2026-12-31T23:30:00Z")));
        assertEquals("1 January 2027", AmsterdamTime.date(Instant.parse("2026-12-31T23:30:00Z")));
        // 22:30 UTC in summer is 00:30 the next day
        assertEquals("16 July 2026", AmsterdamTime.date(Instant.parse("2026-07-15T22:30:00Z")));
        assertEquals("15 July 2026", AmsterdamTime.date(Instant.parse("2026-07-15T21:59:00Z")));
    }
}
