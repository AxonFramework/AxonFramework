package org.axonframework.serializer.xml;

import org.axonframework.serializer.AbstractContentTypeConverter;
import org.axonframework.serializer.CannotConvertBetweenTypesException;
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
public class InputStreamToDom4jConverter extends AbstractContentTypeConverter<InputStream, Document> {

    @Override
    public Class<InputStream> expectedSourceType() {
        return InputStream.class;
    }

    @Override
    public Class<Document> targetType() {
        return Document.class;
    }

    @Override
    public Document convert(InputStream original) {
        try {
            return new STAXEventReader().readDocument(new InputStreamReader(original,
                                                                            Charset.forName("UTF-8")));
        } catch (XMLStreamException e) {
            throw new CannotConvertBetweenTypesException("Cannot convert from InputStream to dom4j Document.", e);
        }
    }
}
