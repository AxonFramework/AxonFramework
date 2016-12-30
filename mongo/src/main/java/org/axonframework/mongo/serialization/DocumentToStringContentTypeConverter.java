/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.serialization;

import org.axonframework.serialization.ContentTypeConverter;
import org.bson.Document;

/**
 * ContentTypeConverter implementation that converts a BSON Document structure into a String containing its JSON
 * representation.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DocumentToStringContentTypeConverter implements ContentTypeConverter<Document,String> {

    @Override
    public Class<Document> expectedSourceType() {
        return Document.class;
    }

    @Override
    public Class<String> targetType() {
        return String.class;
    }

    @Override
    public String convert(Document original) {
        return original.toJson();
    }
}
