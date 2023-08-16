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

package org.axonframework.eventhandling.scheduling.dbscheduler;

import com.github.kagkarlsson.scheduler.serializer.GsonSerializer;
import com.github.kagkarlsson.scheduler.serializer.JacksonSerializer;
import com.github.kagkarlsson.scheduler.serializer.JavaSerializer;
import com.github.kagkarlsson.scheduler.serializer.Serializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DbSchedulerBinaryEventDataTest {

    @MethodSource("serializers")
    @ParameterizedTest
    void shouldBeSerializable(Serializer serializer) {
        DbSchedulerBinaryEventData expected = new DbSchedulerBinaryEventData(
                "payload".getBytes(),
                "class",
                "0",
                "{\"foo\":\"bar\"}".getBytes()
        );
        byte[] serialized = serializer.serialize(expected);
        DbSchedulerBinaryEventData result = serializer.deserialize(DbSchedulerBinaryEventData.class, serialized);
        assertEquals(expected, result);
    }

    public static Collection<Serializer> serializers() {
        List<Serializer> serializers = new ArrayList<>();
        serializers.add(new JavaSerializer());
        serializers.add(new JacksonSerializer());
        serializers.add(new GsonSerializer());
        return serializers;
    }
}
