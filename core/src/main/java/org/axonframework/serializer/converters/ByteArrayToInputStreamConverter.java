package org.axonframework.serializer.converters;

import org.axonframework.serializer.ContentTypeConverter;
import org.axonframework.serializer.IntermediateRepresentation;
import org.axonframework.serializer.SerializedType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * ContentTypeConverter that converts byte arrays into InputStream. More specifically, it returns an
 * ByteArrayInputStream with the underlying byte[] is backing data.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ByteArrayToInputStreamConverter implements ContentTypeConverter<byte[], InputStream> {

    @Override
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    public Class<InputStream> targetType() {
        return InputStream.class;
    }

    @Override
    public IntermediateRepresentation<InputStream> convert(final IntermediateRepresentation<byte[]> original) {
        return new InputStreamIntermediateRepresentation(original);
    }

    private static class InputStreamIntermediateRepresentation implements IntermediateRepresentation<InputStream> {
        private final IntermediateRepresentation<byte[]> original;

        public InputStreamIntermediateRepresentation(IntermediateRepresentation<byte[]> original) {
            this.original = original;
        }

        @Override
        public Class<InputStream> getContentType() {
            return InputStream.class;
        }

        @Override
        public SerializedType getType() {
            return original.getType();
        }

        @Override
        public InputStream getData() {
            return new ByteArrayInputStream(original.getData());
        }
    }
}
