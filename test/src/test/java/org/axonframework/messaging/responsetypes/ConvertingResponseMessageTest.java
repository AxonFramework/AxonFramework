package org.axonframework.messaging.responsetypes;

import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConvertingResponseMessageTest {

    @Test
    void testPayloadIsConvertedToExpectedType() {
        QueryResponseMessage<?> msg = new GenericQueryResponseMessage<>(new String[]{"Some string result"})
                .withMetaData(MetaData.with("test", "value"));
        QueryResponseMessage<List<String>> wrapped = new ConvertingResponseMessage<>(
                ResponseTypes.multipleInstancesOf(String.class),
                msg);

        assertEquals(List.class, wrapped.getPayloadType());
        assertEquals(singletonList("Some string result"), wrapped.getPayload());
        assertEquals("value", wrapped.getMetaData().get("test"));
    }
}