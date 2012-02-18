package org.axonframework.serializer.converters;

import org.axonframework.serializer.AbstractContentTypeConverter;

import java.nio.charset.Charset;

/**
 * ContentTypeConverter that converts byte arrays into Strings. Conversion is done using the UTF-8 character set.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ByteArrayToStringConverter extends AbstractContentTypeConverter<byte[], String> {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Override
    public Class<byte[]> expectedSourceType() {
        return byte[].class;
    }

    @Override
    public Class<String> targetType() {
        return String.class;
    }

    @Override
    public String convert(byte[] original) {
        return new String(original, UTF8);
    }
}
