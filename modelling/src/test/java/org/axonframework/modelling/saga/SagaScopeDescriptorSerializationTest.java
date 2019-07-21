package org.axonframework.modelling.saga;

import static junit.framework.TestCase.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.modelling.OnlyAcceptConstructorPropertiesAnnotation;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests serialization capabilities of {@link SagaScopeDescriptor}.
 * 
 * @author JohT
 */
public class SagaScopeDescriptorSerializationTest {

    private SagaScopeDescriptor testSubject;
    private String expectedType = "sagaType";
    private String expectedIdentifier = "identifier";

    @Before
    public void setUp() {
        testSubject = new SagaScopeDescriptor(expectedType, expectedIdentifier);
    }

    @Test
    public void testJavaSerializationCorrectlySetsIdentifierField() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
        objectOutputStream.writeObject(testSubject);
        objectOutputStream.close();

        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
        SagaScopeDescriptor result = (SagaScopeDescriptor) objectInputStream.readObject();
        objectInputStream.close();

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }

    @Test
    public void testXStreamSerializationWorksAsExpected() {
        XStreamSerializer xStreamSerializer = XStreamSerializer.builder().build();
        xStreamSerializer.getXStream().setClassLoader(this.getClass().getClassLoader());

        SerializedObject<String> serializedObject = xStreamSerializer.serialize(testSubject, String.class);
        SagaScopeDescriptor result = xStreamSerializer.deserialize(serializedObject);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }

    @Test
    public void testJacksonSerializationWorksAsExpected() {
        JacksonSerializer jacksonSerializer = JacksonSerializer.builder().build();

        SerializedObject<String> serializedObject = jacksonSerializer.serialize(testSubject, String.class);
        SagaScopeDescriptor result = jacksonSerializer.deserialize(serializedObject);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }

    @Test
    public void testResponseTypeShouldBeSerializableWithJacksonUsingConstructorProperties() throws IOException {
        ObjectMapper objectMapper = OnlyAcceptConstructorPropertiesAnnotation.attachTo(new ObjectMapper());
        JacksonSerializer jacksonSerializer = JacksonSerializer.builder().objectMapper(objectMapper).build();

        SerializedObject<String> serializedObject = jacksonSerializer.serialize(testSubject, String.class);
        SagaScopeDescriptor result = jacksonSerializer.deserialize(serializedObject);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }
}