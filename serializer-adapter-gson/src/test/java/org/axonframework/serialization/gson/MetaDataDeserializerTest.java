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


package org.axonframework.serialization.gson;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.gson.MetaDataDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class MetaDataDeserializerTest {

    @Mock
    JsonDeserializationContext jsonDeserializationContext;

    @InjectMocks
    final MetaDataDeserializer testSubject = new MetaDataDeserializer();

    @Captor
    ArgumentCaptor<JsonElement> captor;

    @Test
    void testMetaDataSerialization() {
        Map<String, Object> expectedValues = Collections.singletonMap("one", 5.0);
        MetaData expected = new MetaData(expectedValues);

        doReturn(expectedValues).when(jsonDeserializationContext).deserialize(captor.capture(), any());


        JsonElement elem = new Gson().toJsonTree(expected);


        assertEquals(expected, testSubject.deserialize(elem, MetaData.class, jsonDeserializationContext));
        assertEquals(expectedValues, new Gson().fromJson(captor.getValue(), Map.class));
    }
}

