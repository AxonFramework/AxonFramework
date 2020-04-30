/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling.tokenstore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.axonframework.eventhandling.TrackingToken;

import java.util.Map;

/**
 * A special implementation of a Token that is used to store configuration specific to the underlying
 * storage of each TokenStore instance.
 * <p>
 * This class merely implements TrackingToken to adhere to certain API requirements. It is not meant to be used
 * as a means to track progress of event stream processing.
 *
 * @author Allard Buijze
 * @since 4.4
 */
public class ConfigToken implements TrackingToken {

    private final Map<String, String> config;

    /**
     * Initialize a ConfigToken instance using the given {@code config} properties
     *
     * @param config the properties to store as part of this ConfigToken
     */
    @JsonCreator
    public ConfigToken(Map<String, String> config) {
        this.config = config;
    }

    /**
     * Returns the properties contained in this token as a Map.
     *
     * @return the configuration elements in this token
     */
    @JsonValue
    public Map<String, String> getConfig() {
        return config;
    }

    /**
     * Retrieves the value of the configuration element for the given {@code key}.
     *
     * @param key The key for which to retrieve the configuration element
     *
     * @return The configuration element registered under the given key, or {@code null} if no such key was present.
     */
    public String get(String key) {
        return config.get(key);
    }

    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        throw new UnsupportedOperationException("ConfigTokens don't support lowerbound");
    }

    @Override
    public TrackingToken upperBound(TrackingToken other) {
        throw new UnsupportedOperationException("ConfigTokens don't support upperbound");
    }

    @Override
    public boolean covers(TrackingToken other) {
        throw new UnsupportedOperationException("ConfigTokens don't support comparing to other tokens");
    }

}
