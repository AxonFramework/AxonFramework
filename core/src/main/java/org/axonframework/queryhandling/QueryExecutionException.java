package org.axonframework.queryhandling;

/**
 * Exception thrown when execution of the query fails.
 *
 * @since 3.1
 * @author Marc Gathier
 */
public class QueryExecutionException extends RuntimeException {
    public QueryExecutionException(Throwable throwable) {
        super(throwable);
    }
}
