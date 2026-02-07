/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging.commandhandling.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.common.FutureUtils;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.sequencing.CommandSequencingPolicy;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A {@link MessageHandlerInterceptor} implementation to sequence command execution.
 * <p>
 * Commands are sequenced based on a {@link CommandSequencingPolicy} that specifies how to determine the sequence
 * identifier from the {@link CommandMessage} that is being intercepted. Commands with the same sequence identifier will
 * be executed sequentially, meaning that a command will wait for the previous command with the same identifier to
 * complete before starting execution. Commands with different sequence identifiers can be executed concurrently.
 * <p>
 * Commands for which the policy returns {@code Optional.empty()} as sequence identifier will proceed without any
 * sequencing constraints.
 *
 * @param <M> the Message type this interceptor can process
 * @author Jakob Hatzl
 * @since 5.0.3
 */
public class CommandSequencingInterceptor<M extends CommandMessage> implements MessageHandlerInterceptor<M> {

    private static final Logger logger = LoggerFactory.getLogger(CommandSequencingInterceptor.class);

    private final CommandSequencingPolicy sequencingPolicy;
    private final ConcurrentMap<Object, CompletableFuture<Void>> inProgress;

    /**
     * Construct a {@code CommandSequencingInterceptor} that sequences command execution based on the supplied
     * {@link CommandSequencingPolicy}.
     *
     * @param sequencingPolicy the {@link CommandSequencingPolicy} to apply for retrieving the sequence identifier from
     *                         the {@link CommandMessage}.
     */
    public CommandSequencingInterceptor(@Nonnull CommandSequencingPolicy sequencingPolicy) {
        this.sequencingPolicy = sequencingPolicy;
        this.inProgress = new ConcurrentHashMap<>();
    }

    @Override
    public @NonNull MessageStream<?> interceptOnHandle(@NonNull M message, @NonNull ProcessingContext context,
                                                       @NonNull MessageHandlerInterceptorChain<M> interceptorChain) {
        Object sequenceIdentifier = sequencingPolicy.getSequenceIdentifierFor(message, context).orElse(null);
        if (sequenceIdentifier != null) {
            // await turn to sequence command execution
            logger.debug("Sequencing command execution for [{}] in {}", sequenceIdentifier, context);

            // Create done marker for this command
            CompletableFuture<Void> currentLock = new CompletableFuture<>();
            CompletableFuture<Void> previousLock = inProgress.put(sequenceIdentifier, currentLock);

            // Automatic cleanup when current task completes
            currentLock.whenComplete((r, e) -> inProgress.remove(sequenceIdentifier, currentLock));

            // make the previous lock an empty future to complete immediately if none is present
            if (previousLock == null) {
                logger.debug("No previous command execution for [{}] in {}. Processing immediately.",
                             sequenceIdentifier,
                             context);
                previousLock = FutureUtils.emptyCompletedFuture();
            }

            return previousLock
                    .handle((r, e) -> {
                        context.whenComplete(ctx -> {
                            logger.debug(
                                    "Processing command for [{}] completed successfully in {}. Passing lock to next command.",
                                    sequenceIdentifier,
                                    ctx);
                            currentLock.complete(null);
                        });
                        context.onError((ctx, phase, error) -> {
                            logger.debug(
                                    "Processing command for [{}] completed with error in {}. Passing lock to next command.",
                                    sequenceIdentifier,
                                    ctx);
                            currentLock.complete(null);
                        });
                        logger.debug(
                                "Proceeding command execution for [{}] in {}.", sequenceIdentifier, context);
                        return interceptorChain.proceed(message, context);
                    })
                    .join();
        } else {
            logger.debug("Missing sequence identifier, skipping command execution sequencing in {}", context);
            return interceptorChain.proceed(message, context);
        }
    }
}
