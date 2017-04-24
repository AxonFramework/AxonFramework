/*
 * Copyright (c) 2010-2017. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
