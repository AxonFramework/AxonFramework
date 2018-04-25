package org.axonframework.serialization;

import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MessageSerializerTest {

    @Test
    public void testSerializeNullPayload_ShouldMaintainTypeData() {
        GenericMessage<String> nullMessage = new GenericMessage<>(String.class, null, MetaData.emptyInstance());
        SerializedObject<byte[]> actual = new MessageSerializer(new XStreamSerializer()).serializePayload(nullMessage, byte[].class);

        assertEquals("string", actual.getType().getName());
    }
}
