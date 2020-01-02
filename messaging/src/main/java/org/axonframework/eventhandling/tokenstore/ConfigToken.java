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
 * @since 4.3
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
        return false;
    }

}
