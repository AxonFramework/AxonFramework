/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.conversion.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.conversion.ConversionException;
import org.axonframework.conversion.ContentTypeConverter;

/**
 * A {@link ContentTypeConverter} implementation that converts a {@link JsonNode} into an {@link ObjectNode}.
 * <p>
 * Intended to simplify JSON-typed event upcasters, which generally deal with an {@code ObjectNode} as the event.
 * <p>
 * Will succeed if the {@code JsonNode} has a node type of {@link JsonNodeType#OBJECT}.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class JsonNodeToObjectNodeConverter implements ContentTypeConverter<JsonNode, ObjectNode> {

    @Override
    @Nonnull
    public Class<JsonNode> expectedSourceType() {
        return JsonNode.class;
    }

    @Override
    @Nonnull
    public Class<ObjectNode> targetType() {
        return ObjectNode.class;
    }

    @Override
    @Nullable
    public ObjectNode convert(@Nullable JsonNode input) {
        if (input == null) {
            return null;
        }

        JsonNodeType originalNodeType = input.getNodeType();
        if (JsonNodeType.OBJECT.equals(originalNodeType)) {
            return ((ObjectNode) input);
        } else {
            throw new ConversionException(
                    "Cannot convert from JsonNode to ObjectNode because the node type is [" + originalNodeType + "]."
            );
        }
    }
}
