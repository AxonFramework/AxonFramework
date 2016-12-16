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

import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import org.axonframework.serialization.ContentTypeConverter;

/**
 * ContentTypeConverter implementation that converts a DBObject structure into a String containing its Binary JSON
 * representation.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DBObjectToStringContentTypeConverter implements ContentTypeConverter<DBObject,String> {

    @Override
    public Class<DBObject> expectedSourceType() {
        return DBObject.class;
    }

    @Override
    public Class<String> targetType() {
        return String.class;
    }

    @Override
    public String convert(DBObject original) {
        return JSON.serialize(original);
    }
}
