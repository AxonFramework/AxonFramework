/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.scheduling.java;

import org.axonframework.conversion.TestConverter;
import org.axonframework.messaging.eventhandling.scheduling.java.SimpleScheduleToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests conversion capabilities of {@link SimpleScheduleToken}.
 *
 * @author JohT
 */
class SimpleScheduleTokenSerializationTest {

    static Collection<TestConverter> converters() {
       return TestConverter.all();
    }

    @MethodSource("converters")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestConverter converter) {
        SimpleScheduleToken tokenToTest = new SimpleScheduleToken("28bda08d-2dd5-4420-98cb-75ca073446b4");
        assertEquals(tokenToTest, converter.serializeDeserialize(tokenToTest));
    }
}