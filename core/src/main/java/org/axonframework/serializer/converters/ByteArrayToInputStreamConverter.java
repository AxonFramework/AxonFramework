package org.axonframework.serializer.converters;

import org.axonframework.serializer.AbstractContentTypeConverter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * ContentTypeConverter that converts byte arrays into InputStream. More specifically, it returns an
 * ByteArrayInputStream with the underlying byte[] is backing data.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ByteArrayToInputStreamConverter extends AbstractContentTypeConverter<byte[], InputStream> {

    @Override
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    public Class<InputStream> targetType() {
        return InputStream.class;
    }

    @Override
    public InputStream convert(byte[] original) {
        return new ByteArrayInputStream(original);
    }
}
