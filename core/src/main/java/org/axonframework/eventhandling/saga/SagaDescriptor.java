package org.axonframework.eventhandling.saga;

import org.axonframework.messaging.ScopeDescriptor;

import java.util.Objects;

/**
 * Describes the scope of a Saga by means of its type and identifier.
 *
 * @author Steven van Beelen
 * @since 3.3
 */
public class SagaDescriptor implements ScopeDescriptor {

    private static final long serialVersionUID = 4162755498638204691L;

    private final String type;
    private final Object identifier;

    public SagaDescriptor(String type, Object identifier) {
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
        final SagaDescriptor other = (SagaDescriptor) obj;
        return Objects.equals(this.type, other.type)
                && Objects.equals(this.identifier, other.identifier);
    }

    @Override
    public String toString() {
        return "SagaDescriptor{" +
                "type='" + type + '\'' +
                ", identifier=" + identifier +
                '}';
    }
}
