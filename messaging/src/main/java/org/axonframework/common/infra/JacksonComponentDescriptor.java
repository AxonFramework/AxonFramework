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

package org.axonframework.common.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * A {@link ComponentDescriptor} implementation that uses Jackson's {@link ObjectMapper} to create JSON representations
 * of components. This implementation produces a clean, hierarchical JSON structure.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class JacksonComponentDescriptor implements ComponentDescriptor {

    private final ObjectMapper objectMapper;
    private final ObjectNode rootNode;

    /**
     * Constructs a new {@code JacksonComponentDescriptor} with a default {@link ObjectMapper}.
     */
    public JacksonComponentDescriptor() {
        this(new ObjectMapper());
    }

    /**
     * Constructs a new {@code JacksonComponentDescriptor} with the provided {@link ObjectMapper}.
     *
     * @param objectMapper The ObjectMapper to use for JSON serialization
     */
    public JacksonComponentDescriptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.rootNode = objectMapper.createObjectNode();
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Object object) {
        var json = describeObject(object);
        rootNode.set(name, json);
    }

    private JsonNode describeObject(@NotNull Object object) {
        return switch (object) {
            case DescribableComponent c -> describeComponentJson(c);
            default -> objectMapper.valueToTree(object);
        };
    }

    private ObjectNode describeComponentJson(DescribableComponent component) {
        var nestedDescriptor = new JacksonComponentDescriptor(this.objectMapper);
        describeIdAndType(component, nestedDescriptor);
        component.describeTo(nestedDescriptor);
        return nestedDescriptor.rootNode;
    }

    private static void describeIdAndType(
            DescribableComponent component,
            JacksonComponentDescriptor componentDescriptor
    ) {
        componentDescriptor.rootNode.put("_id", System.identityHashCode(component) + "");
        componentDescriptor.rootNode.put("_type", component.getClass().getSimpleName());
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Collection<?> collection) {
        var arrayNode = objectMapper.createArrayNode();
        for (var item : collection) {
            var json = describeObject(item);
            arrayNode.add(json);
        }
        rootNode.set(name, arrayNode);
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Map<?, ?> map) {
        ObjectNode mapNode = objectMapper.createObjectNode();

        for (var entry : map.entrySet()) {
            var key = entry.getKey().toString();
            var value = entry.getValue();
            var json = describeObject(value);
            mapNode.set(key, json);
        }

        rootNode.set(name, mapNode);
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull String value) {
        rootNode.put(name, value);
    }

    @Override
    public void describeProperty(@Nonnull String name, long value) {
        rootNode.put(name, value);
    }

    @Override
    public void describeProperty(@Nonnull String name, boolean value) {
        rootNode.put(name, value);
    }

    @Override
    public String describe() {
        try {
            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            throw new RuntimeException("Error generating JSON description: " + e.getMessage(), e);
        }
    }
}
