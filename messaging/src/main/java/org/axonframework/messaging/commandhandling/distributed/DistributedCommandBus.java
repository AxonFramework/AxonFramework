/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.commandhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.util.PriorityRunnable;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of a {@code CommandBus} that is aware of multiple instances of a {@code CommandBus} working together
 * to spread load.
 * <p>
 * Each "physical" {@code CommandBus} instance is considered a "segment" of a conceptual distributed
 * {@code CommandBus}.
 * <p>
 * The {@code DistributedCommandBus} relies on a {@link CommandBusConnector} to dispatch commands and replies to
 * different segments of the {@code CommandBus}. Depending on the implementation used, each segment may run in a
 * different JVM.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class DistributedCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(DistributedCommandBus.class);

    private final CommandBus localSegment;
    private final CommandBusConnector connector;
    private final ExecutorService executorService;
    private final int loadFactor; // TODO #3074 Refactor to a loadFactor per CommandType.

    /**
     * Constructs a {@code DistributedCommandBus} using the given {@code localSegment} for
     * {@link #subscribe(QualifiedName, CommandHandler) subscribing} handlers and the given {@code connector} to
     * dispatch commands and replies to different segments of the {@code CommandBus}.
     *
     * @param localSegment  The localSegment {@code CommandBus} used to subscribe handlers to.
     * @param connector     The {@code Connector} to dispatch commands or replies.
     * @param configuration The {@code DistributedCommandBusConfiguration} containing the load factor and the
     *                      {@code ExecutorServiceFactory} for this bus.
     */
    public DistributedCommandBus(@Nonnull CommandBus localSegment,
                                 @Nonnull CommandBusConnector connector,
                                 @Nonnull DistributedCommandBusConfiguration configuration) {
        this.localSegment = Objects.requireNonNull(localSegment, "The given CommandBus localSegment cannot be null.");
        this.connector = Objects.requireNonNull(connector, "The given Connector cannot be null.");
        this.loadFactor = configuration.loadFactor();
        this.executorService = configuration.executorServiceFactory()
                                            .createExecutorService(configuration, new PriorityBlockingQueue<>(1000));
        connector.onIncomingCommand(new DistributedHandler());
    }

    @Override
    public DistributedCommandBus subscribe(@Nonnull QualifiedName name,
                                           @Nonnull CommandHandler handler) {
        CommandHandler commandHandler = Objects.requireNonNull(handler, "The given handler cannot be null.");
        localSegment.subscribe(name, commandHandler);
        FutureUtils.joinAndUnwrap(connector.subscribe(name, loadFactor));
        return this;
    }

    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        return connector.dispatch(command, processingContext);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(localSegment);
        descriptor.describeProperty("connector", connector);
    }

    private class DistributedHandler implements CommandBusConnector.Handler {

        private static final AtomicLong TASK_SEQUENCE = new AtomicLong(Long.MIN_VALUE);

        @Override
        public void handle(@Nonnull CommandMessage commandMessage,
                           @Nonnull CommandBusConnector.ResultCallback callback) {
            int priority = commandMessage.priority().orElse(0);
            if (logger.isDebugEnabled()) {
                logger.debug("Received command [{}] for processing with priority [{}] and routing key [{}]",
                             commandMessage.type(),
                             commandMessage.priority().orElse(0),
                             commandMessage.routingKey().orElse(null)
                );
            }
            long sequence = TASK_SEQUENCE.incrementAndGet();
            executorService.execute(new PriorityRunnable(
                    () -> doHandleCommand(commandMessage, callback), priority, sequence
            ));
        }

        private void doHandleCommand(CommandMessage commandMessage,
                                     CommandBusConnector.ResultCallback callback) {
            if (logger.isDebugEnabled()) {
                logger.debug("Processing incoming command [{}] with priority [{}] and routing key [{}]",
                             commandMessage.type(),
                             commandMessage.priority().orElse(0),
                             commandMessage.routingKey().orElse(null));
            }

            localSegment.dispatch(commandMessage, null).whenComplete((resultMessage, e) -> {
                try {
                    if (e == null) {
                        handleSuccess(commandMessage, callback, resultMessage);
                    } else {
                        handleError(commandMessage, callback, e);
                    }
                } catch (Throwable ex) {
                    logger.error("Error handling response of command [{}]", commandMessage.type(), ex);
                    handleError(commandMessage, callback, ex);
                }
            });
        }

        private void handleError(CommandMessage commandMessage, CommandBusConnector.ResultCallback callback,
                                 Throwable e) {
            logger.error("Error processing incoming command [{}]", commandMessage.type(), e);
            callback.onError(e);
        }

        private void handleSuccess(CommandMessage commandMessage,
                                   CommandBusConnector.ResultCallback callback,
                                   CommandResultMessage resultMessage) {
            logger.debug("Successfully processed command [{}] with result [{}]", commandMessage.type(), resultMessage);
            callback.onSuccess(resultMessage);
        }
    }
}