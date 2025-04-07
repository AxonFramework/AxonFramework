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

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockComponentDescriptor implements ComponentDescriptor {

    private Map<String, Object> properties = new ConcurrentHashMap<>();

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    @Override
    public void describeProperty(@Nonnull String name, Object object) {
        properties.put(name, object);
    }

    @Override
    public void describeProperty(@Nonnull String name, Collection<?> collection) {
        properties.put(name, collection);
    }

    @Override
    public void describeProperty(@Nonnull String name, Map<?, ?> map) {
        properties.put(name, map);
    }

    @Override
    public void describeProperty(@Nonnull String name, String value) {
        properties.put(name, value);
    }

    @Override
    public void describeProperty(@Nonnull String name, Long value) {
        properties.put(name, value);
    }

    @Override
    public void describeProperty(@Nonnull String name, Boolean value) {
        properties.put(name, value);
    }

    @Override
    public String describe() {
        return "";
    }
}
