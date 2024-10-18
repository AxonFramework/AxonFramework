package org.axonframework.serialization.avro;

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class AvroSerializerTest {

    private AvroSerializer testSubject;
    private Serializer serializer;

    @BeforeEach
    void setUp() {

        RevisionResolver revisionResolver = payloadType -> "test-revision";
        SchemaStore store = new SchemaStore.Cache();
        serializer = spy(JacksonSerializer.defaultSerializer());

        testSubject = AvroSerializer
            .builder()
            .serializerDelegate(serializer)
            .revisionResolver(revisionResolver)
            .schemaStore(store)
            .build();
    }

    @Test
    public void testBuilder() {
        NullPointerException revisionResolverMandatory = assertThrows(NullPointerException.class, () -> AvroSerializer.builder().build());
        assertEquals("RevisionResolver is mandatory", revisionResolverMandatory.getMessage());

        NullPointerException schemaStoreMandatory = assertThrows(NullPointerException.class, () -> AvroSerializer.builder()
            .revisionResolver(c -> "")
            .build());
        assertEquals("SchemaStore is mandatory", schemaStoreMandatory.getMessage());

        NullPointerException serializerDelegateMandatory = assertThrows(NullPointerException.class, () -> AvroSerializer.builder()
            .revisionResolver(c -> "")
            .schemaStore(new SchemaStore.Cache())
            .build());
        assertEquals("SerializerDelegate is mandatory", serializerDelegateMandatory.getMessage());
    }

    @Test
    public void canSerialize() {
        assertTrue(testSubject.canSerializeTo(byte[].class));
        assertTrue(testSubject.canSerializeTo(GenericRecord.class));

        assertTrue(testSubject.canSerializeTo(String.class));
        assertTrue(testSubject.canSerializeTo(InputStream.class));
    }

    @Test
    public void serializeMetaDataByDelegate() {

        MetaData original = MetaData.from(singletonMap("test", "test"));
        SerializedObject<byte[]> serialized = testSubject.serialize(original, byte[].class);
        MetaData actual = testSubject.deserialize(serialized);

        assertNotNull(actual);
        assertEquals("test", actual.get("test"));
        assertEquals(1, actual.size());

        verify(serializer).serialize(original, byte[].class);
        verify(serializer).deserialize(serialized);
    }

    @Test
    void serializeNull() {
        NullPointerException npe = assertThrows(NullPointerException.class, () -> testSubject.serialize(null, byte[].class));
        assertEquals("Can't serialize a null object", npe.getMessage());
    }

    @Test
    void deserializeEmptyBytes() {
        assertEquals(Void.class, testSubject.classForType(SerializedType.emptyType()));
        assertNull(testSubject.deserialize(
            new SimpleSerializedObject<>(new byte[0], byte[].class, SerializedType.emptyType())
        ));
    }


}
