package org.axonframework.eventstore;

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
