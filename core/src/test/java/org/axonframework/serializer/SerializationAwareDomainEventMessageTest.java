package org.axonframework.serializer;

import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.MetaData;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SerializationAwareDomainEventMessageTest {

    private SerializationAwareEventMessage<String> testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new SerializationAwareDomainEventMessage<String>(
                new GenericDomainEventMessage<String>("aggregate", 1, "payload", Collections.singletonMap("key", "value")));
    }

    @Test
    public void testIsSerializedAsGenericEventMessage() throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(testSubject);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object read = ois.readObject();

        assertEquals(GenericDomainEventMessage.class, read.getClass());
    }

    @Test
    public void testSerializePayloadTwice() {
        Serializer serializer = mock(Serializer.class);
        final SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<byte[]>("payload".getBytes(), byte[].class, "String", "0");
        when(serializer.serialize("payload", byte[].class)).thenReturn(serializedObject);
        testSubject.serializePayload(serializer, byte[].class);
        testSubject.serializePayload(serializer, byte[].class);
        verify(serializer, times(1)).serialize("payload", byte[].class);
        verifyNoMoreInteractions(serializer);
    }

    @Test
    public void testSerializeMetaDataTwice() {
        Serializer serializer = mock(Serializer.class);
        final SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<byte[]>("payload".getBytes(), byte[].class, "String", "0");
        when(serializer.serialize(isA(MetaData.class), eq(byte[].class))).thenReturn(serializedObject);
        testSubject.serializeMetaData(serializer, byte[].class);
        testSubject.serializeMetaData(serializer, byte[].class);
        verify(serializer, times(1)).serialize(isA(MetaData.class), eq(byte[].class));
        verifyNoMoreInteractions(serializer);
    }

    @Test
    public void testSerializePayloadTwice_DifferentRepresentations() {
        Serializer serializer = mock(Serializer.class);
        final SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<byte[]>("payload".getBytes(), byte[].class, "String", "0");
        when(serializer.serialize("payload", byte[].class)).thenReturn(serializedObject);
        SerializedObject<byte[]> actual1 = testSubject.serializePayload(serializer, byte[].class);
        SerializedObject<String> actual2 = testSubject.serializePayload(serializer, String.class);

        assertNotSame(actual1, actual2);
        assertEquals(String.class, actual2.getContentType());
        verify(serializer, times(1)).serialize("payload", byte[].class);
        verifyNoMoreInteractions(serializer);
    }

    @Test
    public void testSerializeMetaDataTwice_DifferentRepresentations() {
        Serializer serializer = mock(Serializer.class);
        final SimpleSerializedObject<byte[]> serializedObject =
                new SimpleSerializedObject<byte[]>("payload".getBytes(), byte[].class, "String", "0");
        when(serializer.serialize(isA(MetaData.class), eq(byte[].class))).thenReturn(serializedObject);
        SerializedObject<byte[]> actual1 = testSubject.serializeMetaData(serializer, byte[].class);
        SerializedObject<String> actual2 = testSubject.serializeMetaData(serializer, String.class);

        assertNotSame(actual1, actual2);
        assertEquals(String.class, actual2.getContentType());
        verify(serializer, times(1)).serialize(isA(MetaData.class), eq(byte[].class));
        verifyNoMoreInteractions(serializer);
    }
}
