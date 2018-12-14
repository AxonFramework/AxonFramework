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

package org.axonframework.eventhandling.scheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.eventhandling.scheduling.quartz.QuartzScheduleToken;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ScheduleTokenJacksonSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationAndDeserializationOfSimpleScheduleToken() throws IOException {
        ScheduleToken scheduleToken = new SimpleScheduleToken("tokenId");

        String serializationResult = objectMapper.writeValueAsString(scheduleToken);
        SimpleScheduleToken deserializationResult = objectMapper.readerFor(SimpleScheduleToken.class)
                                                                .readValue(serializationResult);

        assertEquals(scheduleToken, deserializationResult);
    }

    @Test
    public void testSerializationAndDeserializationOfQuartzScheduleToken() throws IOException {
        ScheduleToken scheduleToken = new QuartzScheduleToken("jobIdentifier", "groupIdentifier");

        String serializationResult = objectMapper.writeValueAsString(scheduleToken);
        QuartzScheduleToken deserializationResult = objectMapper.readerFor(QuartzScheduleToken.class)
                                                                .readValue(serializationResult);

        assertEquals(scheduleToken, deserializationResult);
    }
}
