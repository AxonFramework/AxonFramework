package org.axonframework.serializer;

import org.axonframework.common.Assert;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

import static java.lang.String.format;

/**
 * SerializedObject implementation that takes all properties as constructor parameters.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleSerializedObject implements SerializedObject {

    private final byte[] bytes;
    private final SerializedType type;

    /**
     * Initializes a SimpleSerializedObject using given <code>data</code> and <code>serializedType</code>.
     *
     * @param data           The data of the serialized object
     * @param serializedType The type description of the serialized object
     */
    public SimpleSerializedObject(byte[] data, SerializedType serializedType) {
        Assert.notNull(data, "Data for a serialized object cannot be null");
        Assert.notNull(serializedType, "The type identifier of the serialized object");
        this.bytes = data;
        this.type = serializedType;
    }

    /**
     * Initializes a SimpleSerializedObject using given <code>data</code> and a serialized type identified by given
     * <code>type</code> and <code>revision</code>.
     *
     * @param data     The data of the serialized object
     * @param type     The type identifying the serialized object
     * @param revision The revision number of the serialized object
     */
    public SimpleSerializedObject(byte[] data, String type, int revision) {
        this(data, new SimpleSerializedType(type, revision));
    }

    @Override
    public byte[] getData() {
        return bytes;
    }

    @Override
    public InputStream getStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public SerializedType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SimpleSerializedObject that = (SimpleSerializedObject) o;

        if (!Arrays.equals(bytes, that.bytes)) {
            return false;
        }
        if (!type.equals(that.type)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(bytes);
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return format("SimpleSerializedObject [%s]", type);
    }
}
