package org.axonframework.serialization;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class MessageSerializerTest {

    @Test
    public void testSerializeNullPayload_ShouldMaintainTypeData() {
        GenericMessage<String> nullMessage = new GenericMessage<>(String.class, null, MetaData.emptyInstance());
        SerializedObject<byte[]> actual = new MessageSerializer(new XStreamSerializer()).serializePayload(nullMessage,
                                                                                                          byte[].class);

        assertEquals("string", actual.getType().getName());
    }

    @Test
    public void testMessageSerialization() {
        GenericMessage<String> message = new GenericMessage<>("payload", Collections.singletonMap("key", "value"));
        MessageSerializer messageSerializer = new MessageSerializer(new JacksonSerializer());

        SerializedObject<String> serializedPayload = messageSerializer.serializePayload(message, String.class);
        SerializedObject<String> serializedMetaData = messageSerializer.serializeMetaData(message, String.class);

        assertEquals("\"payload\"", serializedPayload.getData());
        assertEquals("{\"key\":\"value\"}", serializedMetaData.getData());
    }
}
