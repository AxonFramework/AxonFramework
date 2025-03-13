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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

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
    private final Set<Object> visitedObjects;

    /**
     * Constructs a new {@code JacksonComponentDescriptor} with the provided {@link ObjectMapper}.
     *
     * @param objectMapper The ObjectMapper to use for JSON serialization
     */
    public JacksonComponentDescriptor(ObjectMapper objectMapper) {
        this(objectMapper, createVisitedSet());
    }

    /**
     * Constructs a new {@code JacksonComponentDescriptor} with a default {@link ObjectMapper}.
     */
    public JacksonComponentDescriptor() {
        this(createDefaultObjectMapper());
    }

    private JacksonComponentDescriptor(ObjectMapper objectMapper, Set<Object> visitedObjects) {
        this.objectMapper = objectMapper;
        this.rootNode = objectMapper.createObjectNode();
        this.visitedObjects = visitedObjects;
    }

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    private static Set<Object> createVisitedSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Object object) {
        if (object instanceof DescribableComponent component) {
            if (visitedObjects.contains(object)) {
                // Handle circular reference by adding a reference marker
                ObjectNode referenceNode = objectMapper.createObjectNode();
                referenceNode.put("$ref", System.identityHashCode(object) + "");
                rootNode.set(name, referenceNode);
            } else {
                visitedObjects.add(object);
                JacksonComponentDescriptor nestedDescriptor =
                        new JacksonComponentDescriptor(this.objectMapper, visitedObjects);
                component.describeTo(nestedDescriptor);

                // Add type information to help identify the component
                if (!nestedDescriptor.rootNode.has("_type")) {
                    nestedDescriptor.rootNode.put("_type", component.getClass().getSimpleName());
                }

                // Add an object ID for potential references
                nestedDescriptor.rootNode.put("_id", System.identityHashCode(object) + "");

                rootNode.set(name, nestedDescriptor.rootNode);
            }
        } else {
            rootNode.set(name, objectMapper.valueToTree(object));
        }
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Collection<?> collection) {
        ArrayNode arrayNode = objectMapper.createArrayNode();

        for (Object item : collection) {
            if (item instanceof DescribableComponent component) {
                if (visitedObjects.contains(item)) {
                    // Handle circular reference by adding a reference marker
                    ObjectNode referenceNode = objectMapper.createObjectNode();
                    referenceNode.put("$ref", System.identityHashCode(item) + "");
                    arrayNode.add(referenceNode);
                } else {
                    visitedObjects.add(item);
                    JacksonComponentDescriptor itemDescriptor =
                            new JacksonComponentDescriptor(this.objectMapper, visitedObjects);
                    component.describeTo(itemDescriptor);

                    // Add type information
                    if (!itemDescriptor.rootNode.has("_type")) {
                        itemDescriptor.rootNode.put("_type", component.getClass().getSimpleName());
                    }

                    // Add an object ID for potential references
                    itemDescriptor.rootNode.put("_id", System.identityHashCode(item) + "");

                    arrayNode.add(itemDescriptor.rootNode);
                }
            } else {
                arrayNode.add(objectMapper.valueToTree(item));
            }
        }

        rootNode.set(name, arrayNode);
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Map<?, ?> map) {
        ObjectNode mapNode = objectMapper.createObjectNode();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            if (value instanceof DescribableComponent component) {
                if (visitedObjects.contains(value)) {
                    // Handle circular reference by adding a reference marker
                    ObjectNode referenceNode = objectMapper.createObjectNode();
                    referenceNode.put("$ref", System.identityHashCode(value) + "");
                    mapNode.set(key, referenceNode);
                } else {
                    visitedObjects.add(value);
                    JacksonComponentDescriptor valueDescriptor =
                            new JacksonComponentDescriptor(this.objectMapper, visitedObjects);
                    component.describeTo(valueDescriptor);

                    // Add type information
                    if (!valueDescriptor.rootNode.has("_type")) {
                        valueDescriptor.rootNode.put("_type", component.getClass().getSimpleName());
                    }

                    // Add an object ID for potential references
                    valueDescriptor.rootNode.put("_id", System.identityHashCode(value) + "");

                    mapNode.set(key, valueDescriptor.rootNode);
                }
            } else {
                mapNode.set(key, objectMapper.valueToTree(value));
            }
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
