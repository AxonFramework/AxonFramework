package org.axonframework.common;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Some utility methods regarding Date and Time.
 */
public abstract class DateTimeUtils {

    private DateTimeUtils() {
    }

    /**
     * Parse the given {@code timestamp} into an {@link Instant}. Unlike {@link Instant#parse(CharSequence)}, this
     * method accepts ISO8601 timestamps from any offset, <i>not only UTC</i>.
     *
     * @param timestamp an ISO8601 formatted timestamp
     * @return the {@link Instant} representing the given timestamp
     * @see DateTimeFormatter#ISO_OFFSET_DATE_TIME
     */
    public static Instant parseInstant(CharSequence timestamp) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(timestamp, Instant::from);
    }

}
