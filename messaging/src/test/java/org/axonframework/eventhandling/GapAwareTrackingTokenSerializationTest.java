/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.eventhandling;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link GapAwareTrackingToken}.
 *
 * @author JohT
 */
class GapAwareTrackingTokenSerializationTest {

    public static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(Long.MAX_VALUE, asList(0L, 1L));
        assertEquals(subject, serializer.serializeDeserialize(subject));
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenWithoutGapsShouldBeSerializable(TestSerializer serializer) {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(0, emptyList());
        assertEquals(subject, serializer.serializeDeserialize(subject));
    }
}