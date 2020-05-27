package org.axonframework.messaging;

import reactor.core.publisher.Flux;

/**
 * @author Sara Pellegrini
 * @since 4.4
 */
@FunctionalInterface
public interface ReactiveResultHandlerInterceptor<M extends Message<?>, R extends ResultMessage<?>> {

    Flux<R> intercept(M message, Flux<R> results);
}
