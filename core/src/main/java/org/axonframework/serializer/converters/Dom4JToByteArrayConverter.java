package org.axonframework.serializer.converters;

import org.axonframework.common.io.IOUtils;
import org.axonframework.serializer.ContentTypeConverter;
import org.axonframework.serializer.IntermediateRepresentation;
import org.axonframework.serializer.SimpleIntermediateRepresentation;
import org.dom4j.Document;

/**
 * Converter that converts Dom4j Document instances to a byte array. The Document is written as XML string, and
 * converted to bytes using the UTF-8 character set.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class Dom4JToByteArrayConverter implements ContentTypeConverter<Document, byte[]> {

    @Override
    public Class<Document> expectedSourceType() {
        return Document.class;
    }

    @Override
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    public IntermediateRepresentation<byte[]> convert(final IntermediateRepresentation<Document> original) {
        return new SimpleIntermediateRepresentation<byte[]>(original.getType(), byte[].class,
                                                            original.getData().asXML().getBytes(IOUtils.UTF8));
    }
}
