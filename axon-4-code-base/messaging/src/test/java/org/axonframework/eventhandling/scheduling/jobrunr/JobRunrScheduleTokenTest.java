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

package org.axonframework.eventhandling.scheduling.jobrunr;

import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobRunrScheduleTokenTest {

    @Test
    void equalsIfSameUuidIsUsed() {
        UUID uuid = UUID.randomUUID();
        ScheduleToken one = new JobRunrScheduleToken(uuid);
        ScheduleToken other = new JobRunrScheduleToken(uuid);

        assertEquals(one, other);
        assertEquals(one.toString(), other.toString());
        assertEquals(one.hashCode(), other.hashCode());
    }

    @Test
    void notEqualsIfDifferentUuidIsUsed() {
        ScheduleToken one = new JobRunrScheduleToken(UUID.randomUUID());
        ScheduleToken other = new JobRunrScheduleToken(UUID.randomUUID());

        assertNotEquals(one, other);
        assertNotEquals(one.toString(), other.toString());
        assertNotEquals(one.hashCode(), other.hashCode());
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        ScheduleToken tokenToTest = new JobRunrScheduleToken(UUID.randomUUID());
        assertEquals(tokenToTest, serializer.serializeDeserialize(tokenToTest));
    }

    public static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }
}
