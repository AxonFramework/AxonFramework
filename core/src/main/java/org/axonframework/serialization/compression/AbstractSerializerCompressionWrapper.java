package org.axonframework.serialization.compression;

import org.axonframework.serialization.*;

/**
 * Wrapper around an embedded {@link Serializer} that uses a byte[] as a working format
 * to compress the {@link SerializedObject} from the embedded serializer. Implementations must implement the
 * {@code doCompress} and {@code doDecompress} methods.
 *
 * @author Michael Willemse
 */
public abstract class AbstractSerializerCompressionWrapper implements Serializer {

    private final Serializer embeddedSerializer;
    private final Converter converter;

    /**
     * Initializes AbstractByteArrayCompressionSerializer implementation with the embedded serializer as parameter
     * @param embeddedSerializer serializer that must be able to serialize to a byte[] otherwise the constructor
     *                           throws an IllegalArgumentException.
     * @param converter          converter able to convert to and from byte[], the working format in this serialization
     *                           wrapper.
     */
    public AbstractSerializerCompressionWrapper(Serializer embeddedSerializer, Converter converter) {
        if(!embeddedSerializer.canSerializeTo(byte[].class)) {
            throw new IllegalArgumentException("Expecting serializer that can serialize to byte array.");
        }
        this.embeddedSerializer = embeddedSerializer;
        this.converter = converter;
    }

    @Override
    public <T> SerializedObject<T> serialize(Object object, Class<T> expectedRepresentation) {
        if(converter.canConvert(byte[].class, expectedRepresentation)) {
            SerializedObject<byte[]> embeddedSerializedObject = embeddedSerializer.serialize(object, byte[].class);
            try {
                SerializedObject<byte[]> compressedSerializedObject = new SimpleSerializedObject<>(
                        doCompress(embeddedSerializedObject.getData()),
                        embeddedSerializedObject.getContentType(),
                        embeddedSerializedObject.getType());
                return converter.convert(compressedSerializedObject, expectedRepresentation);
            } catch (Throwable e) {
                throw new SerializationException("Unhandled Exception while compressing.", e);
            }
        } else {
            throw new SerializationException("Converter unable to convert from byte[] after compressing.");
        }
    }

    @Override
    public <T> boolean canSerializeTo(Class<T> expectedRepresentation) {
        return converter.canConvert(byte[].class, expectedRepresentation);
    }

    @Override
    public <S, T> T deserialize(SerializedObject<S> serializedObject) {
        if(converter.canConvert(serializedObject.getContentType(), byte[].class)) {
            try {
                SerializedObject<byte[]> convertedSerializedObject = converter.convert(serializedObject, byte[].class);
                SerializedObject<byte[]> decompressedSerializedObject = new SimpleSerializedObject<>(
                        doDecompress (convertedSerializedObject.getData()),
                        convertedSerializedObject.getContentType(),
                        convertedSerializedObject.getType()
                );
                return embeddedSerializer.deserialize(decompressedSerializedObject);
            } catch (Throwable e)  {
                throw new SerializationException("Unhandled Exception while decompressing.", e);
            }
        } else {
            throw new SerializationException("Converter unable to convert to byte[] prior to decompressing.");
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
     * Throws Throwable on a non recoverable exception.
     *
     * @param uncompressedData uncompressed data
     * @return compressed data
     * @throws Throwable
     */
    protected abstract byte[] doCompress(byte[] uncompressedData) throws Throwable;

    /**
     * Decompress the given {@code compressedData} and return the decompressed data
     * Throws Throwable o an non recoverable exception.
     *
     * @param compressedData compressed data
     * @return decompressed data
     * @throws Throwable
     */
    protected abstract byte[] doDecompress(byte[] compressedData) throws Throwable;
}
