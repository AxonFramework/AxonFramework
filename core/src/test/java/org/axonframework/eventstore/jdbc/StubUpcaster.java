/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.jdbc;

import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.SerializedType;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.SimpleSerializedType;
import org.axonframework.upcasting.Upcaster;
import org.axonframework.upcasting.UpcastingContext;

import java.util.Arrays;
import java.util.List;

/**
 * @author Rene de Waele
 */
public class StubUpcaster implements Upcaster<byte[]> {

    @Override
    public boolean canUpcast(SerializedType serializedType) {
        return "java.lang.String".equals(serializedType.getName());
    }

    @Override
    public Class<byte[]> expectedRepresentationType() {
        return byte[].class;
    }

    @Override
    public List<SerializedObject<?>> upcast(SerializedObject<byte[]> intermediateRepresentation,
                                            List<SerializedType> expectedTypes, UpcastingContext context) {
        return Arrays.<SerializedObject<?>>asList(
                new SimpleSerializedObject<>("data1", String.class, expectedTypes.get(0)),
                new SimpleSerializedObject<>(intermediateRepresentation.getData(), byte[].class,
                                             expectedTypes.get(1)));
    }

    @Override
    public List<SerializedType> upcast(SerializedType serializedType) {
        return Arrays.<SerializedType>asList(new SimpleSerializedType("unknownType1", "2"),
                new SimpleSerializedType(StubStateChangedEvent.class.getName(), "2"));
    }
}
