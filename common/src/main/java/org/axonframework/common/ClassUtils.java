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

package org.axonframework.common;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for creating classes by name containing a class cache.
 */
public final class ClassUtils {

    private static ClassLoader classLoader = ClassUtils.class.getClassLoader();
    private static ConcurrentHashMap<String, Class<?>> cache = new ConcurrentHashMap<>();

    /**
     * Avoid instantiation.
     */
    private ClassUtils() {
    }


    /**
     * Loads a class by name
     *
     * @param className The name of the class to load.
     * @param <C>       The class type.
     * @return The loaded class.
     */
    public static <C> Class<C> loadClass(String className) {
        //noinspection unchecked
        return (Class<C>) cache.computeIfAbsent(className, (name) -> {
            try {
                return classLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Could not load class " + name, e);
            }
        });
    }
}
