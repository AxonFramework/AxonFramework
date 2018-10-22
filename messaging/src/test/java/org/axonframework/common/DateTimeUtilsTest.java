/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoField;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DateTimeUtilsTest {

    @Test
    public void testFormattedDateAlwaysContainsMillis() {
        Instant now = Instant.now();
        Instant nowAtZeroMillis = now.minusNanos(now.get(ChronoField.NANO_OF_SECOND));

        String formatted = DateTimeUtils.formatInstant(nowAtZeroMillis);
        assertTrue("Time doesn't seem to contain explicit millis: " + formatted, formatted.matches(".*\\.0{3,}Z"));

        assertEquals(nowAtZeroMillis, DateTimeUtils.parseInstant(formatted));
    }
}
