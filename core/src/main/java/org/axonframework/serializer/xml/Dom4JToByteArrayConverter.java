package org.axonframework.serializer.xml;

import org.axonframework.common.io.IOUtils;
import org.axonframework.serializer.AbstractContentTypeConverter;
import org.dom4j.Document;

/**
 * Converter that converts Dom4j Document instances to a byte array. The Document is written as XML string, and
 * converted to bytes using the UTF-8 character set.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class Dom4JToByteArrayConverter extends AbstractContentTypeConverter<Document, byte[]> {

    @Override
    public Class<Document> expectedSourceType() {
        return Document.class;
    }

    @Override
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    public byte[] convert(Document original) {
        return original.asXML().getBytes(IOUtils.UTF8);
    }
}
