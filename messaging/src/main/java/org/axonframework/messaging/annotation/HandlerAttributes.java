package org.axonframework.messaging.annotation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Container for message handler attributes. Can store several {@link Map}s of {@link String} to {@link Object} under
 * their own key, resembling a message handling method which serves several handling functions.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class HandlerAttributes {

    private final Map<String, Map<String, Object>> handlerAttributes;

    /**
     * Constructs an empty handler attributes object.
     */
    public HandlerAttributes() {
        this(new HashMap<>());
    }

    /**
     * Construct a handler attributes object based on the given {@code handlerAttributes} {@link Map}.
     *
     * @param handlerAttributes a {@link Map} from handler type to attributes {@code Map} to base a {@link
     *                          HandlerAttributes} on
     */
    public HandlerAttributes(Map<String, Map<String, Object>> handlerAttributes) {
        this.handlerAttributes = handlerAttributes;
    }

    /**
     * Put new {@code attributes} for the given {@code handlerType}.
     *
     * @param handlerType the type of handler to add {@code attributes} for
     * @param attributes  the {@link Map} of attribute information to add for the given {@code handlerType}
     */
    public void put(String handlerType, Map<String, Object> attributes) {
        handlerAttributes.put(handlerType, attributes);
    }

    /**
     * Retrieve the attributes for the given {@code handlerType}. Might be {@code null} if there are no attributes
     * present for the specified type.
     *
     * @param handlerType the type of handler to retrieve attributes for
     * @return the attributes for the given {@code handlerType}
     */
    public Map<String, Object> get(String handlerType) {
        return Collections.unmodifiableMap(handlerAttributes.get(handlerType));
    }

    /**
     * Retrieve all attributes for all handler types stored in this {@link HandlerAttributes} object. The returned
     * {@link Map} contains a {@link Map} of attribute name to attribute value, per handler type present.
     *
     * @return all attributes for all handler types stored in this {@link HandlerAttributes} object
     */
    public Map<String, Map<String, Object>> getAll() {
        return Collections.unmodifiableMap(handlerAttributes);
    }

    /**
     * Retrieve all attributes for all handler types stored in this {@link HandlerAttributes} object in a prefixed
     * format. The returned {@link Map} contains keys representing a format of {@code "[handlerType].[attributeName]"}
     * with the respective attribute value corresponding to the attribute name.
     * <p>
     * Serves the purpose a of providing a {@code Map} containing a property file like structure.
     *
     * @return all attributes for all handler types stored in this {@link HandlerAttributes} object in a prefixed format
     */
    public Map<String, Object> getAllPrefixed() {
        Map<String, Object> allPrefixedAttributes = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> attributeEntry : handlerAttributes.entrySet()) {
            String handlerType = attributeEntry.getKey();
            Map<String, Object> prefixedAttributes =
                    attributeEntry.getValue().entrySet().stream()
                                  .collect(Collectors.toMap(
                                          entry -> prefixedKey(handlerType, entry.getKey()),
                                          Map.Entry::getValue
                                  ));
            allPrefixedAttributes.putAll(prefixedAttributes);
        }
        return Collections.unmodifiableMap(allPrefixedAttributes);
    }

    private String prefixedKey(String handlerType, String attributeName) {
        return handlerType + "." + attributeName;
    }

    /**
     * Validates whether the given {@code handlerType} has attributes in this object.
     *
     * @param handlerType the type of handler to validate if it has attributes present in this object
     * @return {@code true} if there are attributes for the given {@code handlerType} present, {@code false} otherwise
     */
    public boolean containsAttributesFor(String handlerType) {
        return handlerAttributes.containsKey(handlerType);
    }

    /**
     * Validate whether zero attributes are present in this object.
     *
     * @return {@code true} if there are no attributes present, {@code false} otherwise
     */
    public boolean isEmpty() {
        return handlerAttributes.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HandlerAttributes that = (HandlerAttributes) o;
        return Objects.equals(handlerAttributes, that.handlerAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handlerAttributes);
    }

    @Override
    public String toString() {
        return "HandlerAttributes{" +
                "handlerAttributes=" + handlerAttributes +
                '}';
    }
}
