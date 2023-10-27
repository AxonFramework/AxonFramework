package org.axonframework.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface Process extends Function<ProcessingContext, CompletableFuture<?>> {

}
