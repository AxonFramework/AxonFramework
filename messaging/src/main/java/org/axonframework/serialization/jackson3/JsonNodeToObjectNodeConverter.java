/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.serialization.jackson3;

import org.axonframework.serialization.ContentTypeConverter;
import org.axonframework.serialization.SerializationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;
import tools.jackson.databind.node.ObjectNode;

/**
 * A {@link ContentTypeConverter} implementation for Jackson 3 that converts a {@link JsonNode} object
 * into an {@link ObjectNode}. Intended to simplify JSON-typed event upcasters, which generally deal
 * with an {@code ObjectNode} as the event.
 * <p>
 * Will succeed if the {@code JsonNode} has a node type of {@link JsonNodeType#OBJECT}.
 *
 * @author Steven van Beelen
 * @author John Hendrikx
 * @since 4.13.0
 */
public class JsonNodeToObjectNodeConverter implements ContentTypeConverter<JsonNode, ObjectNode> {

    @Override
    public Class<JsonNode> expectedSourceType() {
        return JsonNode.class;
    }

    @Override
    public Class<ObjectNode> targetType() {
        return ObjectNode.class;
    }

    @Override
    public ObjectNode convert(JsonNode original) {
        JsonNodeType originalNodeType = original.getNodeType();
        if (JsonNodeType.OBJECT.equals(originalNodeType)) {
            return ((ObjectNode) original);
        } else {
            throw new SerializationException(
                    "Cannot convert from JsonNode to ObjectNode because the node type is [" + originalNodeType + "]"
            );
        }
    }
}
