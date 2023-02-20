package org.axonframework.messaging;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Simple implementation of the {@link HandlerAttributes} which is given the {@code attributes} in the constructor.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class SimpleHandlerAttributes implements HandlerAttributes {

    private final Map<String, Object> attributes;

    /**
     * Constructs a {@link SimpleHandlerAttributes} using the given {@code attributes}. Changes made on the given {@code
     * attributes} after construction of a {@code SimpleHandlerAttributes} are not reflected by the constructed
     * instance.
     *
     * @param attributes the attributes for this {@link HandlerAttributes} implementation
     */
    public SimpleHandlerAttributes(Map<String, Object> attributes) {
        this.attributes = new HashMap<>(attributes);
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

    @Override
    public HandlerAttributes mergedWith(HandlerAttributes other) {
        if (other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }

        HashMap<String, Object> combinedAttributes = new HashMap<>(this.getAll());
        combinedAttributes.putAll(other.getAll());
        return new SimpleHandlerAttributes(combinedAttributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleHandlerAttributes that = (SimpleHandlerAttributes) o;
        return Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes);
    }

    @Override
    public String toString() {
        return "SimpleHandlerAttributes{" +
                "attributes=" + attributes +
                '}';
    }
}
