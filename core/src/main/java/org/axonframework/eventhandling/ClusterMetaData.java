/*
 * Copyright (c) 2010-2012. Axon Framework
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

/**
 * Allows arbitrary information to be attached to a cluster.
 *
 * @author Allard Buijze
 * @since 1.2
 */
public interface ClusterMetaData {

    /**
     * Returns the property stored using the given {@code key}. If no property has been stored using that key, it
     * returns {@code null}.
     *
     * @param key The key under which the property was stored
     * @return The value stored under the given {@code key}
     */
    Object getProperty(String key);

    /**
     * Stores a property {@code value} under the given {@code key}.
     *
     * @param key   the key under which to store a value
     * @param value the value to store
     */
    void setProperty(String key, Object value);

    /**
     * Removes the value store under the given {@code key}. If no such value is available, nothing happens.
     *
     * @param key the key to remove
     */
    void removeProperty(String key);

    /**
     * Indicates whether a value is stored under the given {@code key}. Will also return {@code true} if a {@code null}
     * value is stored under the given {@code key}.
     *
     * @param key The key to find
     * @return {@code true} if a value was stored under the given {@code key}, otherwise {@code false}.
     */
    boolean isPropertySet(String key);
}
