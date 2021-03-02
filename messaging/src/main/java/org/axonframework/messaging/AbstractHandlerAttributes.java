package org.axonframework.messaging;

import java.util.Collections;
import java.util.Map;

/**
 * Abstract implementation of {@link HandlerAttributes}. Contains a {@link Map} of {@link String} to {@link Object} to
 * carry the attributes in.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public abstract class AbstractHandlerAttributes implements HandlerAttributes {

    protected final Map<String, Object> attributes;

    /**
     * Constructs a {@link AbstractHandlerAttributes} using the given {@code attributes}.
     *
     * @param attributes the attributes for this {@link HandlerAttributes} implementation
     */
    protected AbstractHandlerAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> R get(String attributeKey) {
        return (R) attributes.get(attributeKey);
    }

    @Override
    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public boolean contains(String attributeKey) {
        return attributes.containsKey(attributeKey);
    }

    @Override
    public boolean isEmpty() {
        return attributes.isEmpty();
    }
}
