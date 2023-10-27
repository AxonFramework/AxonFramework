/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.serialization.xml;

import org.axonframework.serialization.CannotConvertBetweenTypesException;
import org.axonframework.serialization.ContentTypeConverter;
import org.dom4j.Document;
import org.dom4j.io.STAXEventReader;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Converter that converts an input stream to a Dom4J document. It assumes that the input stream provides UTF-8
 * formatted XML.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class InputStreamToDom4jConverter implements ContentTypeConverter<InputStream,Document> {

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
