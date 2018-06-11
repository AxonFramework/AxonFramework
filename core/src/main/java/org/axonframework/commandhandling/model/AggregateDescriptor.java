package org.axonframework.commandhandling.model;

import org.axonframework.messaging.ScopeDescriptor;

import java.util.Objects;

/**
 * Describes the scope of an Aggregate by means of its type and identifier.
 *
 * @author Steven van Beelen
 * @since 3.3
 */
public class AggregateDescriptor implements ScopeDescriptor {

    private static final long serialVersionUID = 3584695571254668002L;

    private final String type;
    private final Object identifier;

    public AggregateDescriptor(String type, Object identifier) {
        this.type = type;
        this.identifier = identifier;
    }

    public String getType() {
        return type;
    }

    public Object getIdentifier() {
        return identifier;
    }

    @Override
    public String scopeDescription() {
        return String.format("AggregateDescriptor for type [%s] and identifier [%s]", type, identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, identifier);
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
