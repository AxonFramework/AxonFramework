package org.axonframework.serialization.compression;

import org.axonframework.serialization.*;

import java.io.IOException;

public abstract class ByteArrayCompressionSerializer implements Serializer {

    protected Serializer embeddedSerializer;

    public ByteArrayCompressionSerializer(Serializer embeddedSerializer) {
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
            } catch (IOException e) {
                throw new SerializationException("Unhandled IOException while compressing.", e);
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
            } catch (IOException e)  {
                throw new SerializationException("Unhandled IOException while decompressing.", e);
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

    public Serializer getEmbeddedSerializer() {
        return embeddedSerializer;
    }

    protected abstract byte[] doCompress(byte[] uncompressed) throws IOException;

    protected abstract byte[] doDecompress(byte[] compressed) throws IOException;
}
