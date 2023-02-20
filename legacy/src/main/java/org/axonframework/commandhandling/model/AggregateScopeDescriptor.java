package org.axonframework.commandhandling.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Implementation of the {@link org.axonframework.modelling.command.AggregateScopeDescriptor} used to bridge serialized
 * versions of this descriptor when migrating from Axon 3.x to Axon 4.x.
 *
 * @author Steven van Beelen
 * @since 4.2
 * @deprecated in favor of the {@link org.axonframework.modelling.command.AggregateScopeDescriptor}
 */
@Deprecated
public class AggregateScopeDescriptor extends org.axonframework.modelling.command.AggregateScopeDescriptor {

    // Fields {@code type} and {@code identifier} are used during Java and XStream de-/serialization through methods
    // {@link #readObject(ObjectInputStream)} and {@link #readResolve()}.
    @SuppressWarnings("unused")
    private String type;
    @SuppressWarnings("unused")
    private Object identifier;

    /**
     * Instantiate a AggregateScopeDescriptor with the provided {@code type} and {@code identifier}.
     *
     * @param type       A {@link String} describing the type of the Saga
     * @param identifier An {@link Object} denoting the identifier of the Saga
     */
    @JsonCreator
    public AggregateScopeDescriptor(@JsonProperty("type") String type, @JsonProperty("identifier") Object identifier) {
        super(type, identifier);
    }

    private void readObject(ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException {
        objectInputStream.defaultReadObject();
    }

    private Object readResolve() {
        return new org.axonframework.modelling.command.AggregateScopeDescriptor(type, identifier);
    }
}
