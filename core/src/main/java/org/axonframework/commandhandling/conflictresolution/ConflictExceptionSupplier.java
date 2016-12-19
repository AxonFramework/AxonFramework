package org.axonframework.commandhandling.conflictresolution;

/**
 * Interface describing a factory for exceptions that indicate an unresolved conflict in an aggregate instance.
 *
 * @param <T> The type of exception created
 */
@FunctionalInterface
public interface ConflictExceptionSupplier<T extends Exception> {

    /**
     * Creaates an instance of an exception indicating a conflict in an aggregate with given {@code aggregateIdentifier},
     * the given {@code expectedVersion} and {@code actualVersion}.
     *
     * @param aggregateIdentifier The identifier of the conflicting aggregate
     * @param expectedVersion     The expected version of the aggregate
     * @param actualVersion       The actual version of the aggregate
     * @return the exception describing the conflict
     */
    T supplyException(String aggregateIdentifier, long expectedVersion, long actualVersion);
}
