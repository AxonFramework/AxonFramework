/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.TemporalAccessor;

import static java.time.temporal.ChronoField.*;

/**
 * Some utility methods regarding Date and Time.
 *
 * @author Allard Buijze
 * @since 3.1
 */
public abstract class DateTimeUtils {

    private static final DateTimeFormatter ISO_UTC_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendFraction(NANO_OF_SECOND, 3, 3, true)
            .appendOffsetId()
            .toFormatter()
            .withZone(ZoneOffset.UTC);

    private DateTimeUtils() {
        // Utility class
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

    /**
     * Formats the given instant to ISO8601 format including three positions for milliseconds. This is to provide
     * for a String representation of an instant that is sortable by a textual sorting algorithm.
     *
     * @param instant the instant to represent as ISO8601 data
     * @return the ISO8601 representation of the instant in the UTC timezone
     */
    public static String formatInstant(TemporalAccessor instant) {
        return ISO_UTC_DATE_TIME.format(instant);
    }
}
