/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.config;

import java.util.Collections;
import java.util.Map;

/**
 * Tags configuration labeling Axon Node represented by key-value pairs.
 *
 * @author Milan Savic
 * @since 4.2
 */
public class TagsConfiguration {

    /**
     * Tags represented by key-value pairs.
     */
    private final Map<String, String> tags;

    /**
     * The default constructor.
     */
    public TagsConfiguration() {
        this(Collections.emptyMap());
    }

    /**
     * Initializes tags configuration with key-value pairs.
     *
     * @param tags the map of {@link String} to {@link String} representing tags key-value pairs
     */
    public TagsConfiguration(Map<String, String> tags) {
        this.tags = tags;
    }

    /**
     * Gets tags.
     *
     * @return the map of {@link String} to {@link String} representing tags key-value pairs
     */
    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(tags);
    }
}
