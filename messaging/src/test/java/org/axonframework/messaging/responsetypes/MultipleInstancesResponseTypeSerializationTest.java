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

package org.axonframework.messaging.responsetypes;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link MultipleInstancesResponseType}.
 *
 * @author JohT
 */
class MultipleInstancesResponseTypeSerializationTest extends AbstractResponseTypeTest<List<AbstractResponseTypeTest.QueryResponse>> {

    MultipleInstancesResponseTypeSerializationTest() {
        super(new MultipleInstancesResponseType<>(QueryResponse.class));
    }

    static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void responseTypeShouldBeSerializable(TestSerializer serializer) {
        assertEquals(testSubject.getExpectedResponseType(), serializer.serializeDeserialize(testSubject).getExpectedResponseType());
    }
}
