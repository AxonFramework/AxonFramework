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

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A {@link ComponentDescriptor} implementation that uses Jackson's {@link ObjectMapper} to create JSON representations
 * of components. This implementation produces a clean, hierarchical JSON structure.
 * <p>
 * This implementation supports circular references between components by using a reference mechanism. When a
 * {@link DescribableComponent} is encountered for the first time, it is fully serialized including its unique
 * identifier ({@code _id}). Any subsequent occurrences of the same component instance are replaced with a reference
 * object containing a {@code $ref} field pointing to the original component's identifier and a {@code _type} field
 * indicating the component's type. This prevents infinite recursion and {@link StackOverflowError} when describing
 * components with circular dependencies.
 * <p>
 * Example JSON with a circular reference:
 * <pre>
 * {
 *   "component": {
 *     "_id": "12345",
 *     "_type": "MyComponent",
 *     "name": "First Component",
 *     "reference": {
 *       "_id": "67890",
 *       "_type": "MyComponent",
 *       "name": "Second Component",
 *       "backReference": {
 *         "$ref": "12345",
 *         "_type": "MyComponent"
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class JacksonComponentDescriptor implements ComponentDescriptor {

    private final ObjectMapper objectMapper;
    private final ObjectNode rootNode;
    private final Map<DescribableComponent, String> processedComponents;

    /**
     * Constructs a new {@code JacksonComponentDescriptor} with a default {@link ObjectMapper}.
     */
    public JacksonComponentDescriptor() {
        this(new ObjectMapper());
    }

    /**
     * Constructs a new {@code JacksonComponentDescriptor} with the provided {@link ObjectMapper}.
     *
     * @param objectMapper The ObjectMapper to use for JSON serialization.
     */
    public JacksonComponentDescriptor(ObjectMapper objectMapper) {
        this(objectMapper, new IdentityHashMap<>());
    }

    /**
     * Private constructor used for creating nested descriptors that share the processed components map.
     *
     * @param objectMapper        The ObjectMapper to use for JSON serialization.
     * @param processedComponents Map containing already processed components and their IDs.
     */
    private JacksonComponentDescriptor(ObjectMapper objectMapper,
                                       Map<DescribableComponent, String> processedComponents) {
        this.objectMapper = objectMapper;
        this.rootNode = objectMapper.createObjectNode();
        this.processedComponents = processedComponents;
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Object object) {
        var json = describeObject(object);
        rootNode.set(name, json);
    }

    private JsonNode describeObject(Object object) {
        if (object instanceof DescribableComponent component) {
            var id = processedComponents.get(component);
            var componentSeenAlready = id != null;
            if (componentSeenAlready) {
                var refNode = objectMapper.createObjectNode();
                refNode.put("$ref", id);
                describeType(component, refNode);
                return refNode;
            }
            return describeComponentJson(component);
        }
        return objectMapper.valueToTree(object);
    }

    private ObjectNode describeComponentJson(DescribableComponent component) {
        var componentId = System.identityHashCode(component) + "";

        // Register this component before processing its properties.
        // This prevents infinite recursion with circular references.
        processedComponents.put(component, componentId);

        var nestedDescriptor = new JacksonComponentDescriptor(this.objectMapper, this.processedComponents);
        describeIdAndType(component, nestedDescriptor);
        component.describeTo(nestedDescriptor);
        return nestedDescriptor.rootNode;
    }

    private static void describeIdAndType(
            DescribableComponent component,
            JacksonComponentDescriptor componentDescriptor
    ) {
        componentDescriptor.rootNode.put("_id", System.identityHashCode(component) + "");
        describeType(component, componentDescriptor.rootNode);
    }

    private static void describeType(DescribableComponent component, ObjectNode objectNode) {
        objectNode.put("_type", component.getClass().getSimpleName());
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