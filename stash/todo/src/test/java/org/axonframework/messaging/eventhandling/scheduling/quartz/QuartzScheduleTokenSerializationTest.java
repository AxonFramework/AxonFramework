/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.scheduling.quartz;

import org.axonframework.conversion.TestConverter;
import org.axonframework.messaging.eventhandling.scheduling.quartz.QuartzScheduleToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests conversion capabilities of {@link QuartzScheduleToken}.
 *
 * @author JohT
 */
class QuartzScheduleTokenSerializationTest {

    public static Collection<TestConverter> converters() {
       return TestConverter.all();
    }

    @MethodSource("converters")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestConverter converter) {
        QuartzScheduleToken tokenToTest = new QuartzScheduleToken("jobIdentifier", "groupIdentifier");
        assertEquals(tokenToTest, converter.serializeDeserialize(tokenToTest));
    }
}