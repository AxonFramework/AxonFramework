/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.modelling.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.modelling.OnlyAcceptConstructorPropertiesAnnotation;
import org.axonframework.modelling.utils.TestSerializer;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests serialization capabilities of {@link SagaScopeDescriptor}.
 * 
 * @author JohT
 */
class SagaScopeDescriptorSerializationTest {

    private final String expectedType = "sagaType";
    private final String expectedIdentifier = "identifier";

    private SagaScopeDescriptor testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new SagaScopeDescriptor(expectedType, expectedIdentifier);
    }

    @Test
    void javaSerializationCorrectlySetsIdentifierField() throws Exception {
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
    void xStreamSerializationWorksAsExpected() {
        XStreamSerializer xStreamSerializer = TestSerializer.xStreamSerializer();
        xStreamSerializer.getXStream().setClassLoader(this.getClass().getClassLoader());

        SerializedObject<String> serializedObject = xStreamSerializer.serialize(testSubject, String.class);
        SagaScopeDescriptor result = xStreamSerializer.deserialize(serializedObject);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }

    @Test
    void jacksonSerializationWorksAsExpected() {
        JacksonSerializer jacksonSerializer = JacksonSerializer.defaultSerializer();

        SerializedObject<String> serializedObject = jacksonSerializer.serialize(testSubject, String.class);
        SagaScopeDescriptor result = jacksonSerializer.deserialize(serializedObject);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }

    @Test
    void responseTypeShouldBeSerializableWithJacksonUsingConstructorProperties() {
        ObjectMapper objectMapper = OnlyAcceptConstructorPropertiesAnnotation.attachTo(new ObjectMapper());
        JacksonSerializer jacksonSerializer = JacksonSerializer.builder().objectMapper(objectMapper).build();

        SerializedObject<String> serializedObject = jacksonSerializer.serialize(testSubject, String.class);
        SagaScopeDescriptor result = jacksonSerializer.deserialize(serializedObject);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }
}