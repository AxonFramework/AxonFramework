package org.axonframework.queryhandling.serialize;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

public class SerializationUtil {
    private SerializationUtil() {
        // utility
    }

    public static <Q> SerializedObject<byte[]> serialize(Q query, Serializer serializer) {
        return serializer.serialize(query, byte[].class);
    }
}
