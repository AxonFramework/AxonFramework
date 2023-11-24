package org.axonframework.messaging.annotation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Steven van Beelen
 * @author Sara Pellegrini
 * @param <T>
 */
@FunctionalInterface
public interface MessageHandlerInvoker<T> {

    CompletableFuture<Object> invoke(T target, List<Object> parameters);
}
