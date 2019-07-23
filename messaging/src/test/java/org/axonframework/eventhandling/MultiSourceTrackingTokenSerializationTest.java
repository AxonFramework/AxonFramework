/*
 * Copyright (c) 2010-2019. Axon Framework
 *
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

package org.axonframework.eventhandling;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

import org.axonframework.serialization.TestSerializer;

/**
 * Tests serialization capabilities of {@link MultiSourceTrackingToken}.
 * 
 * @author JohT
 */
@RunWith(Parameterized.class)
public class MultiSourceTrackingTokenSerializationTest {

    private MultiSourceTrackingToken testSubject;
    
    private final TestSerializer serializer;

    public MultiSourceTrackingTokenSerializationTest(TestSerializer serializer) {
        this.serializer = serializer;
    }

    @Parameterized.Parameters(name = "{index} {0}")
    public static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }

    @Test
    public void testTokenShouldBeSerializable() {
        Map<String, TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", new GlobalSequenceTrackingToken(0));
        tokenMap.put("token2", new GlobalSequenceTrackingToken(0));
        testSubject = new MultiSourceTrackingToken(tokenMap);
        assertEquals(testSubject, serializer.serializeDeserialize(testSubject));
    }
}