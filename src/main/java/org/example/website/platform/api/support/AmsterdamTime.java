package org.example.website.platform.api.support;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shows moments in Europe/Amsterdam time, including the change between summer and winter time, in a
 * form that cannot be misread (plan: Language, dates and time zone). The server and the database
 * keep moments in UTC and only convert them for display.
 */
public final class AmsterdamTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZONE);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZONE);

    private AmsterdamTime() {}

    /** For example "Sunday 13 December 2026, 20:00". */
    public static String dateTime(Instant moment) {
        return DATE_TIME.format(moment);
    }

    /** For example "13 December 2026". */
    public static String date(Instant moment) {
        return DATE.format(moment);
    }
}
