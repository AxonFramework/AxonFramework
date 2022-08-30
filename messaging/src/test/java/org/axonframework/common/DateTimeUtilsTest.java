/*
 * Copyright (c) 2010-2021. Axon Framework
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

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.temporal.ChronoField;

import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DateTimeUtils}.
 *
 * @author Allard Buijze
 */
class DateTimeUtilsTest {

    @Test
    void formattedDateAlwaysContainsMillis() {
        Instant now = Instant.now();
        Instant nowAtZeroMillis = now.minusNanos(now.get(ChronoField.NANO_OF_SECOND));

        String formatted = formatInstant(nowAtZeroMillis);
        assertTrue(formatted.matches(".*\\.0{3,}Z"), "Time doesn't seem to contain explicit millis: " + formatted);

        assertEquals(nowAtZeroMillis, DateTimeUtils.parseInstant(formatted));
    }

    @Test
    void formatInstantHasFixedPrecisionAtThree() {
        String expectedDateTimeString = "2021-03-22T15:41:02.101Z";

        Instant testInstantWithTrailingZeroes = Instant.parse("2021-03-22T15:41:02.101900Z");
        Instant testInstantWithTrailingNonZeroes = Instant.parse("2021-03-22T15:41:02.101911Z");
        Instant testInstantWithoutTrailingZeroes = Instant.parse("2021-03-22T15:41:02.1019Z");
        Instant testInstantWithoutMillis = Instant.parse("2021-03-22T15:41:02Z");

        assertEquals(expectedDateTimeString, formatInstant(testInstantWithTrailingZeroes));
        assertEquals(expectedDateTimeString, formatInstant(testInstantWithTrailingNonZeroes));
        assertEquals(expectedDateTimeString, formatInstant(testInstantWithoutTrailingZeroes));
        assertEquals("2021-03-22T15:41:02.000Z", formatInstant(testInstantWithoutMillis));
    }
}
