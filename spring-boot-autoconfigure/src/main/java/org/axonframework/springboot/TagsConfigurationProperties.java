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

package org.axonframework.springboot;

import org.axonframework.axonserver.connector.TagsConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Configuration properties for {@link TagsConfiguration}.
 *
 * @author Milan Savic
 * @since 4.2
 */
@ConfigurationProperties(prefix = "axon")
public class TagsConfigurationProperties {

    /**
     * Tags represented by key-value pairs.
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * Gets tags.
     *
     * @return the map of {@link String} to {@link String} representing tags key-value pairs
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * Sets tags.
     *
     * @param tags the map of {@link String} to {@link String} representing tags key-value pairs
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public TagsConfiguration toTagsConfiguration() {
        return new TagsConfiguration(tags);
    }
}
