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

    public JacksonComponentDescriptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.rootNode = objectMapper.createObjectNode();
    }

    public JacksonComponentDescriptor() {
        this(new ObjectMapper());
    }


    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Object object) {
        if (object instanceof DescribableComponent nestedComponent) {
            JacksonComponentDescriptor nestedDescriptor = new JacksonComponentDescriptor(this.objectMapper);
            nestedComponent.describeTo(nestedDescriptor);
            rootNode.set(name, nestedDescriptor.rootNode);
        } else {
            rootNode.set(name, objectMapper.valueToTree(object));
        }
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Collection<?> collection) {
        var arrayNode = objectMapper.createArrayNode();

        for (var item : collection) {
            if (item instanceof DescribableComponent itemComponent) {
                JacksonComponentDescriptor itemDescriptor = new JacksonComponentDescriptor();
                itemComponent.describeTo(itemDescriptor);
                arrayNode.add(itemDescriptor.rootNode);
            } else {
                arrayNode.add(objectMapper.valueToTree(item));
            }
        }

        rootNode.set(name, arrayNode);
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Map<?, ?> map) {
        var mapNode = objectMapper.createObjectNode();

        for (var entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            if (value instanceof DescribableComponent valueComponent) {
                JacksonComponentDescriptor valueDescriptor = new JacksonComponentDescriptor();
                valueComponent.describeTo(valueDescriptor);
                mapNode.set(key, valueDescriptor.rootNode);
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
            throw new RuntimeException(
                    "Error generating JSON description: " + e.getMessage()); // todo: custom exception
        }
    }
}
