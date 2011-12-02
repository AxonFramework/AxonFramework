package org.axonframework.serializer;

import org.axonframework.domain.MetaData;

import java.io.InputStream;

/**
 * Represents the serialized form of a {@link MetaData} instance.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerializedMetaData implements SerializedObject {

    private final SimpleSerializedObject delegate;

    /**
     * Construct an instance with given <code>bytes</code> representing the serialized form of a {@link MetaData}
     * instance.
     *
     * @param bytes data representing the serialized form of a {@link MetaData} instance.
     */
    public SerializedMetaData(byte[] bytes) {
        delegate = new SimpleSerializedObject(bytes, MetaData.class.getName(), -1);
    }

    @Override
    public byte[] getData() {
        return delegate.getData();
    }

    @Override
    public InputStream getStream() {
        return delegate.getStream();
    }

    @Override
    public SerializedType getType() {
        return delegate.getType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SerializedMetaData that = (SerializedMetaData) o;

        if (!delegate.equals(that.delegate)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
