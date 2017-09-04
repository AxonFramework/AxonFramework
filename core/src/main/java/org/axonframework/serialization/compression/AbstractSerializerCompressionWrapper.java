package org.axonframework.serialization.compression;

import org.axonframework.serialization.*;

/**
 * Wrapper around an embedded {@link Serializer} that can serialize to a byte array. Implementations must implement the
 * {@code doCompress} and {@code doDecompress} methods. When a NotCompressedException occurs the {@code deserialize}
 * method tries to deserialize using the {@code embeddedSerializer} without decompression.
 *
 * @author Michael Willemse
 */
public abstract class AbstractSerializerCompressionWrapper implements Serializer {

    protected Serializer embeddedSerializer;

    /**
     * Initializes AbstractByteArrayCompressionSerializer implementation with the embedded serializer as parameter
     * @param embeddedSerializer serializer that must be able to serialize to a byte array otherwise the constructor
     *                           throws an IllegalArgumentException.
     */
    public AbstractSerializerCompressionWrapper(Serializer embeddedSerializer) {
        if(!embeddedSerializer.canSerializeTo(byte[].class)) {
            throw new IllegalArgumentException("Expecting serializer that serializes to byte array.");
        }
        this.embeddedSerializer = embeddedSerializer;
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, Class<T> expectedRepresentation) {
        SerializedObject<T> embeddedSerializedObject = embeddedSerializer.serialize(object, expectedRepresentation);
        if(byte[].class.equals(expectedRepresentation)) {
            try {
                return new SimpleSerializedObject<>(
                        (T) doCompress((byte[]) embeddedSerializedObject.getData()),
                        embeddedSerializedObject.getContentType(),
                        embeddedSerializedObject.getType());
            } catch (Throwable e) {
                throw new SerializationException("Unhandled Exception while compressing.", e);
            }
        } else {
            return embeddedSerializedObject;
        }
    }

    @Override
    public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
        return embeddedSerializer.canSerializeTo(expectedRepresentation);
    }

    @Override
    public <S, T> T deserialize(SerializedObject<S> serializedObject) {
        if(byte[].class.equals(serializedObject.getContentType())) {
            try {
                SerializedObject<S> decompressedSerializedObject = new SimpleSerializedObject<>(
                        (S) doDecompress((byte[]) serializedObject.getData()),
                        serializedObject.getContentType(),
                        serializedObject.getType()
                );
                return embeddedSerializer.deserialize(decompressedSerializedObject);

            } catch (NotCompressedException e) {
                //Content wasn't compressed or failure based on compression occurred.
                return embeddedSerializer.deserialize(serializedObject);
            } catch (Throwable e)  {
                throw new SerializationException("Unhandled Exception while decompressing.", e);
            }

        } else {
            return embeddedSerializer.deserialize(serializedObject);
        }
    }

    @Override
    public Class classForType(SerializedType type) throws UnknownSerializedTypeException {
        return embeddedSerializer.classForType(type);
    }

    @Override
    public SerializedType typeForClass(Class type) {
        return embeddedSerializer.typeForClass(type);
    }

    @Override
    public Converter getConverter() {
        return embeddedSerializer.getConverter();
    }

    /**
     * Returns the embedded serializer used by the AbstractByteArrayCompressionSerializer implementation.
     *
     * @return embedded serializer
     */
    public Serializer getEmbeddedSerializer() {
        return embeddedSerializer;
    }

    /**
     * Compress the given {@code uncompressedData} and return the compressed data
     * Throws  Throwable on an non recoverable exception.
     *
     * @param uncompressedData uncompressed data
     * @return compressed data
     * @throws Throwable
     */
    protected abstract byte[] doCompress(byte[] uncompressedData) throws Throwable;

    /**
     * Decompress the given {@code compressedData} and return the decompressed data
     * Throws {@link NotCompressedException} when data is not compressed and Throwable on an non recoverable exception.
     *
     * @param compressedData compressed data
     * @return decompressed data
     * @throws Throwable
     */
    protected abstract byte[] doDecompress(byte[] compressedData) throws Throwable;
}
