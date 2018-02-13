/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.command;

import io.axoniq.platform.SerializedObject;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;

import static org.junit.Assert.assertEquals;

/**
 * Author: marc
 */
public class CommandSerializerTest {

    private CommandSerializer serializer;

    @Before
    public void init() {
        serializer = new CommandSerializer(new XStreamSerializer());
    }

    @org.junit.Test
    public void serializePayload() throws Exception {
        SerializedObject serialized = serializer.serializePayload("Test");
        Object deserialized = serializer.deserializePayload(serialized);
        assertEquals("Test", deserialized);

    }



}