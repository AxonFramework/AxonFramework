/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventhandling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of ClusterMetaData. It is backed by a ConcurrentHashMap but allows the storage of {@code
 * null} values. {@code null} keys are not allowed.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public class DefaultClusterMetaData implements ClusterMetaData {

    private Map<String, Object> properties = new ConcurrentHashMap<String, Object>();
    private static final Object NULL_REPLACEMENT = new Object();

    @Override
    public Object getProperty(String key) {
        Object value = properties.get(key);
        return value == NULL_REPLACEMENT ? null : value; // NOSONAR Intentional
    }

    @Override
    public void setProperty(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("null is not allowed as key");
        }
        if (value == null) {
            value = NULL_REPLACEMENT;
        }
        properties.put(key, value);
    }

    @Override
    public void removeProperty(String key) {
        properties.remove(key);
    }

    @Override
    public boolean isPropertySet(String key) {
        return properties.containsKey(key);
    }
}
