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
public class AggregateDescriptor implements ScopeDescriptor {

    private static final long serialVersionUID = 3584695571254668002L;

    private final String type;
    private Object identifier;
    private transient Supplier<Object> identifierSupplier;

    public AggregateDescriptor(String type, Supplier<Object> identifierSupplier) {
        notNull(
                identifierSupplier,
                () -> "A Supplier for the identifier field is required when using this constructor"
        );

        this.type = type;
        this.identifierSupplier = identifierSupplier;
    }

    @JsonCreator
    public AggregateDescriptor(@JsonProperty("type") String type, @JsonProperty("identifier") Object identifier) {
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
        return String.format("AggregateDescriptor for type [%s] and identifier [%s]", type, identifier);
    }

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
        final AggregateDescriptor other = (AggregateDescriptor) obj;
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
