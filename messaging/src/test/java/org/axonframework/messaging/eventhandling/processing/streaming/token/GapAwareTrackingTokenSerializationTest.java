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

package org.axonframework.messaging.eventhandling.processing.streaming.token;

import org.axonframework.conversion.TestConverter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests conversion capabilities of {@link GapAwareTrackingToken}.
 *
 * @author JohT
 */
class GapAwareTrackingTokenSerializationTest {

    public static Collection<TestConverter> converters() {
        return TestConverter.all();
    }

    @MethodSource("converters")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestConverter converter) {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(Long.MAX_VALUE, asList(0L, 1L));
        assertEquals(subject, converter.serializeDeserialize(subject));
    }

    @MethodSource("converters")
    @ParameterizedTest
    void tokenWithoutGapsShouldBeSerializable(TestConverter converter) {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(0, emptyList());
        assertEquals(subject, converter.serializeDeserialize(subject));
    }
}