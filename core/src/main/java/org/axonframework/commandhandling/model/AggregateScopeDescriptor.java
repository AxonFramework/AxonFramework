package org.axonframework.commandhandling.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.messaging.ScopeDescriptor;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

import static org.axonframework.common.Assert.notNull;

/**
 * Describes the scope of an Aggregate by means of its type and identifier.
 *
 * @author Steven van Beelen
 * @since 3.3
 */
public class AggregateScopeDescriptor implements ScopeDescriptor {

    private static final long serialVersionUID = 3584695571254668002L;

    private final String type;
    private Object identifier;
    private transient Supplier<Object> identifierSupplier;

    /**
     * Instantiate an AggregateScopeDescriptor with a {@code type} and {@ocde identifierSupplier}. Using the {@ocde
     * identifierSupplier} instead of an {@link Object} for the {@code identifier} allows the creating processes to
     * provide the identifier lazily.
     * This is necessary when Aggregate's identifier is not create yet, for example when the AggregateScopeDescriptor is
     * created whilst the Aggregate is still under construction.
     *
     * @param type               A {@link String} describing the type of the Aggregate
     * @param identifierSupplier A {@link Supplier} of {@link Object}, which can supply the identifier of the Aggregate
     */
    public AggregateScopeDescriptor(String type, Supplier<Object> identifierSupplier) {
        notNull(
                identifierSupplier,
                () -> "A Supplier for the identifier field is required when using this constructor"
        );

        this.type = type;
        this.identifierSupplier = identifierSupplier;
    }

    /**
     * Instantiate an AggregateScopeDescriptor with the provided {@code type} and {@ocde identifier}.
     *
     * @param type       A {@link String} describing the type of the Aggregate
     * @param identifier An {@link Object} denoting the identifier of the Aggregate
     */
    @JsonCreator
    public AggregateScopeDescriptor(@JsonProperty("type") String type, @JsonProperty("identifier") Object identifier) {
        this.type = type;
        this.identifier = identifier;
    }

    public String getType() {
        return type;
    }

    public Object getIdentifier() {
        if (identifier == null) {
            identifier = identifierSupplier.get();
        }
        return identifier;
    }

    @Override
    public String scopeDescription() {
        return String.format("AggregateScopeDescriptor for type [%s] and identifier [%s]", type, identifier);
    }

    /**
     * This function is provided for Java serialization, such that it will ensure the {@code identifierSupplier} is
     * called, thus setting the {@code identifier}, prior to serializing this AggregateScopeDescriptor.
     */
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        getIdentifier();
        out.defaultWriteObject();
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, identifierSupplier);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final AggregateScopeDescriptor other = (AggregateScopeDescriptor) obj;
        return Objects.equals(this.type, other.type)
                && Objects.equals(this.identifier, other.identifier);
    }

    @Override
    public String toString() {
        return "AggregateScopeDescriptor{" +
                "type=" + type +
                ", identifier='" + identifier + '\'' +
                '}';
    }
}
