package org.axonframework.serializer.converters;

import java.nio.charset.Charset;

/**
 * ContentTypeConverter that converts String into byte arrays. Conversion is done using the UTF-8 character set.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class StringToByteArrayConverter extends AbstractContentTypeConverter<String, byte[]> {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Override
    public Class<String> expectedSourceType() {
        return String.class;
    }

    @Override
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    public byte[] convert(String original) {
        return original.getBytes(UTF8);
    }
}
