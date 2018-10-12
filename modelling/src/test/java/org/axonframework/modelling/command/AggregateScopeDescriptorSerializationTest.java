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

package org.axonframework.modelling.command;

import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;

/**
 * This test class tests whether the {@link AggregateScopeDescriptor} is serializable as expected, by Java, XStream and
 * Jackson. It does so because an AggregateScopeDescriptor can be instantiated with a {@link
 * java.util.function.Supplier} for the {@code identifier}. We do not want to serialize a Supplier, but rather the
 * actual identifier it supplies, hence functionality is added which ensure the Supplier is called to fill the {@code
 * identifier} field just prior to the complete serialization. This test ensure this works as designed.
 */
public class AggregateScopeDescriptorSerializationTest {

    private AggregateScopeDescriptor testSubject;
    private String expectedType = "aggregateType";
    private String expectedIdentifier = "identifier";

    @Before
    public void setUp() {
        testSubject = new AggregateScopeDescriptor(expectedType, () -> expectedIdentifier);
    }

    @Test
    public void testJavaSerializationCorrectlySetsIdentifierField() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
        objectOutputStream.writeObject(testSubject);
        objectOutputStream.close();

        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
        AggregateScopeDescriptor result = (AggregateScopeDescriptor) objectInputStream.readObject();
        objectInputStream.close();

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }

    @Test
    public void testXStreamSerializationWorksAsExpected() {
        XStreamSerializer xStreamSerializer = XStreamSerializer.builder().build();
        xStreamSerializer.getXStream().setClassLoader(this.getClass().getClassLoader());

        SerializedObject<String> serializedObject =
                xStreamSerializer.serialize(testSubject, String.class);
        AggregateScopeDescriptor result = xStreamSerializer.deserialize(serializedObject);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }

    @Test
    public void testJacksonSerializationWorksAsExpected() {
        JacksonSerializer jacksonSerializer = JacksonSerializer.builder().build();


        SerializedObject<String> serializedObject = jacksonSerializer.serialize(testSubject, String.class);
        AggregateScopeDescriptor result = jacksonSerializer.deserialize(serializedObject);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedIdentifier, result.getIdentifier());
    }
}
