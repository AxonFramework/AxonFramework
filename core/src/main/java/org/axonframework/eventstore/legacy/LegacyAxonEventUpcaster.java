/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.legacy;

import org.axonframework.eventstore.EventUpcaster;
import org.dom4j.Document;
import org.dom4j.Element;

import java.util.Iterator;

/**
 * Event preprocessor that upcasts events serialized by the XStreamEventSerializer in versions 0.6 and prior of
 * AxonFramework, to the event format supported since 0.7.
 * <p/>
 * This upcaster uses dom4j Document as event representation, which is supported by the {@link
 * org.axonframework.eventstore.XStreamEventSerializer}.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class LegacyAxonEventUpcaster implements EventUpcaster<Document> {

    @Override
    public Class<Document> getSupportedRepresentation() {
        return Document.class;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Document upcast(Document event) {
        Element rootNode = event.getRootElement();
        if (rootNode.attribute("eventRevision") == null) {
            Element metaData = rootNode.addElement("metaData").addElement("values");
            Iterator<Element> children = rootNode.elementIterator();
            while (children.hasNext()) {
                Element childNode = children.next();
                String childName = childNode.getName();
                if ("eventIdentifier".equals(childName)) {
                    addMetaDataEntry(metaData, "_identifier", childNode.getTextTrim(), "uuid");
                    rootNode.remove(childNode);
                } else if ("timestamp".equals(childName)) {
                    addMetaDataEntry(metaData, "_timestamp", childNode.getTextTrim(), "localDateTime");
                    rootNode.remove(childNode);
                }
            }
        }
        return event;
    }

    private void addMetaDataEntry(Element metaData, String key, String value, String keyType) {
        Element entry = metaData.addElement("entry");
        entry.addElement("string").setText(key);
        entry.addElement(keyType).setText(value);
    }
}
