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
package org.axonframework.messaging.responsetypes;

import org.axonframework.messaging.IllegalPayloadAccessException;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

class ConvertingResponseMessageTest {

    @Test
    void payloadIsConvertedToExpectedType() {
        QueryResponseMessage<?> msg = new GenericQueryResponseMessage<>(new String[]{"Some string result"})
                .withMetaData(MetaData.with("test", "value"));
        QueryResponseMessage<List<String>> wrapped = new ConvertingResponseMessage<>(
                ResponseTypes.multipleInstancesOf(String.class),
                msg);

        assertEquals(List.class, wrapped.getPayloadType());
        assertEquals(singletonList("Some string result"), wrapped.getPayload());
        assertEquals("value", wrapped.getMetaData().get("test"));
    }

    @Test
    void illegalAccessPayloadWhenResultIsExceptional() {
        QueryResponseMessage<?> msg = GenericQueryResponseMessage.asResponseMessage(List.class, new RuntimeException());
        QueryResponseMessage<List<String>> wrapped = new ConvertingResponseMessage<>(
                ResponseTypes.multipleInstancesOf(String.class),
                msg);

        assertEquals(List.class, wrapped.getPayloadType());
        assertThrows(IllegalPayloadAccessException.class, wrapped::getPayload);
    }
}