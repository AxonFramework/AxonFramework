package org.axonframework.commandhandling.distributed.registry;

import org.axonframework.commandhandling.CommandMessage;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A endpoint in the network for a command handling service
 * @param <T> The type of the identifier of this entry in the set of nodes
 */
public class ServiceMember<T> {
    private final T identifier;
    private final Predicate<CommandMessage> commandFilter;
    private final int loadFactor;

    /**
     * Create the service member
     * @param identifier    The identifier of this endpoint
     * @param commandFilter The filter for the commands to accept
     * @param loadFactor    The load factor of this endpoint
     */
    public ServiceMember(T identifier, Predicate<CommandMessage> commandFilter, int loadFactor) {
        this.identifier = identifier;
        this.commandFilter = commandFilter;
        this.loadFactor = loadFactor;
    }

    /**
     * Get the command filter for this endpoint
     * @return The command filter for this endpoint
     */
    public Predicate<CommandMessage> getCommandFilter() {
        return commandFilter;
    }

    /**
     * Get the load factor for this endpoint
     * @return The load factor for this endpoint
     */
    public int getLoadFactor() {
        return loadFactor;
    }

    /**
     * Get the identifier for this endpoint. Usually this will be a URI
     * @return The identifier for this endpoint
     */
    public T getIdentifier() {
        return identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceMember<?> that = (ServiceMember<?>) o;
        return loadFactor == that.loadFactor &&
                Objects.equals(identifier, that.identifier) &&
                Objects.equals(commandFilter, that.commandFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, commandFilter, loadFactor);
    }

    @Override
    public String toString() {
        return "ServiceMember{" +
                "identifier=" + identifier +
                ", commandFilter=" + commandFilter +
                ", loadFactor=" + loadFactor +
                '}';
    }
}
