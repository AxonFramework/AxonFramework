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

package org.axonframework.extension.spring.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Jackson deserializer for the Spring Data {@link Page} interface.
 * <p>
 * This deserializer converts JSON representations of paginated data into {@link PageImpl} instances. It extracts
 * the {@code content} array, {@code number} (page number), {@code size} (page size), and {@code totalElements}
 * from the JSON structure.
 * <p>
 * The deserializer handles missing fields gracefully by applying sensible defaults:
 * <ul>
 *     <li>{@code number} defaults to 0</li>
 *     <li>{@code size} defaults to the content size (minimum 1)</li>
 *     <li>{@code totalElements} defaults to the content size</li>
 * </ul>
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
public class JacksonPageDeserializer extends JsonDeserializer<Page<?>> {

    @Override
    public Page<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        List<Object> content = new ArrayList<>();
        JsonNode contentNode = node.get("content");
        if (contentNode != null && contentNode.isArray()) {
            contentNode.forEach(content::add);
        }

        int page = node.has("number") ? node.get("number").asInt() : 0;
        int size = node.has("size") ? node.get("size").asInt() : Math.max(content.size(), 1);
        long totalElements = node.has("totalElements") ? node.get("totalElements").asLong() : content.size();

        return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }
}
