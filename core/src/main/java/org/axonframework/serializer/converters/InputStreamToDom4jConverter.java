package org.axonframework.serializer.converters;

import org.axonframework.serializer.CannotConvertBetweenTypesException;
import org.axonframework.serializer.ContentTypeConverter;
import org.axonframework.serializer.IntermediateRepresentation;
import org.axonframework.serializer.SimpleIntermediateRepresentation;
import org.dom4j.Document;
import org.dom4j.io.STAXEventReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import javax.xml.stream.XMLStreamException;

/**
 * Converter that converts an input stream to a Dom4J document. It assumes that the input stream provides UTF-8
 * formatted XML.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class InputStreamToDom4jConverter implements ContentTypeConverter<InputStream, Document> {

    @Override
    public Class<InputStream> expectedSourceType() {
        return InputStream.class;
    }

    @Override
    public Class<Document> targetType() {
        return Document.class;
    }

    @Override
    public IntermediateRepresentation<Document> convert(IntermediateRepresentation<InputStream> original) {
        try {
            Document document = new STAXEventReader().readDocument(new InputStreamReader(original.getData(),
                                                                                         Charset.forName("UTF-8")));
            return new SimpleIntermediateRepresentation<Document>(original.getType(), Document.class, document);
        } catch (XMLStreamException e) {
            throw new CannotConvertBetweenTypesException("Cannot convert from InputStream to dom4j Document.", e);
        }
    }
}
