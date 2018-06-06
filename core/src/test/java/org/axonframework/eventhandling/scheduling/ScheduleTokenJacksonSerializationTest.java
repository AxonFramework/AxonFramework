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
