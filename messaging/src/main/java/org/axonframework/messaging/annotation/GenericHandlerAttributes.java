package org.axonframework.messaging.annotation;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generic implementation of the {@link HandlerAttributes} which should be given the attributes in the constructor.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class GenericHandlerAttributes implements HandlerAttributes {

    private final Map<String, Object> attributes;

    /**
     * Constructs an empty {@link GenericHandlerAttributes}.
     */
    public GenericHandlerAttributes() {
        this(Collections.emptyMap());
    }

    /**
     * Constructs a {@link GenericHandlerAttributes} using the given {@code attributes}.
     *
     * @param attributes the attributes for this {@link HandlerAttributes} implementation
     */
    public GenericHandlerAttributes(Map<String, Object> attributes) {
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

    /**
     * Returns a {@link GenericHandlerAttributes}, merging the attributes in {@code this} instance with the given {@code
     * attributes}.
     *
     * @param additionalAttributes the attributes to group with {@code this} instance's attributes
     * @return a {@link GenericHandlerAttributes} combining {@code this} instance's attributes and the given {@code
     * additionalAttributes}
     */
    public GenericHandlerAttributes mergedWith(Map<String, Object> additionalAttributes) {
        if (additionalAttributes.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return new GenericHandlerAttributes(additionalAttributes);
        }

        HashMap<String, Object> combinedAttributes = new HashMap<>(this.getAll());
        combinedAttributes.putAll(additionalAttributes);
        return new GenericHandlerAttributes(combinedAttributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GenericHandlerAttributes that = (GenericHandlerAttributes) o;
        return Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes);
    }

    @Override
    public String toString() {
        return "GenericHandlerAttributes{" +
                "attributes=" + attributes +
                '}';
    }
}
