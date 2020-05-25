package org.axonframework.messaging;

import reactor.core.publisher.Flux;

import java.util.function.BiFunction;

/**
 * @author Sara Pellegrini
 * @since 4.4
 */
@FunctionalInterface
public interface ReactiveResultHandlerInterceptor<M extends  Message<?>, R extends ResultMessage<?>>
        extends BiFunction<M, Flux<R>, Flux<R>> {

    /**
     * Intercepts a flux of results.
     *
     * @param message the message that has been sent to obtain the results
     * @param results a {@link Flux} of a results to be intercepted
     * @return the result {@link Flux} to handle
     */
    default Flux<R> intercept(M message, Flux<R> results) {
        return apply(message, results);
    }

}
