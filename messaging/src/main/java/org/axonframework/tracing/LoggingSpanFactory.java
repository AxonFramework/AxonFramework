/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.tracing;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Implementation of a {@link SpanFactory} that requires no java agent or APM system, only logging. Will log tracing
 * information to info level, with the following identifying prefix: {@code {spanId}{spanName}}.
 * <p>
 * When traces are related to a message, the message type and identifier are logged as well. If a message is dispatched
 * while currently handling a message in the {@link org.axonframework.messaging.unitofwork.UnitOfWork}, it will log the
 * information regarding the message being handled as well.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
public class LoggingSpanFactory implements SpanFactory {

    /**
     * Singleton instance of the {@link LoggingSpanFactory}.
     */
    public static final LoggingSpanFactory INSTANCE = new LoggingSpanFactory();

    private static final Logger logger = LoggerFactory.getLogger(LoggingSpanFactory.class);

    private LoggingSpanFactory() {
    }

    @Override
    public Span createRootTrace(Supplier<String> operationNameSupplier) {
        return new Slf4jSpan(operationNameSupplier, () -> "Root trace started");
    }

    @Override
    public Span createHandlerSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                  boolean isChildTrace, Message<?>... linkedParents) {
        return new Slf4jSpan(operationNameSupplier,
                             () -> String.format("Handler span started for message of type [%s] and identifier [%s]",
                                                 parentMessage.getClass().getSimpleName(),
                                                 parentMessage.getIdentifier()));
    }

    @Override
    public Span createDispatchSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                   Message<?>... linkedSiblings) {
        return new Slf4jSpan(operationNameSupplier, () -> getSpanMessage("Dispatch", parentMessage));
    }

    private String getSpanMessage(String spanType, Message<?> parentMessage) {
        return CurrentUnitOfWork
                .map(uow -> String.format(
                        "%s span started for message of type [%s] and identifier [%s] while handling message of type [%s] and identifier [%s]",
                        spanType,
                        parentMessage.getClass().getSimpleName(),
                        parentMessage.getIdentifier(),
                        uow.getMessage().getClass().getSimpleName(),
                        uow.getMessage().getIdentifier()))
                .orElseGet(() -> String.format(
                        "%s span started for message of type [%s] and identifier [%s]",
                        spanType,
                        parentMessage.getClass().getSimpleName(),
                        parentMessage.getIdentifier()));
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier) {
        return new Slf4jSpan(operationNameSupplier,
                             () -> CurrentUnitOfWork
                                     .map(uow -> String.format(
                                             "Internal span started while handling message of type [%s] and identifier [%s]",
                                             uow.getMessage().getClass().getSimpleName(),
                                             uow.getMessage().getIdentifier()))
                                     .orElseGet(() -> "Internal span started"));
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier, Message<?> message) {
        return new Slf4jSpan(operationNameSupplier, () -> getSpanMessage("Internal", message));
    }

    @Override
    public void registerSpanAttributeProvider(SpanAttributesProvider provider) {
        // No-op for now
    }

    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        return message;
    }

    private static class Slf4jSpan implements Span {

        protected final String identifier;
        protected final String name;
        protected final Supplier<String> startLog;

        private Slf4jSpan(Supplier<String> nameSupplier, Supplier<String> startLog) {
            this.startLog = startLog;
            this.identifier = IdentifierFactory.getInstance().generateIdentifier();
            this.name = nameSupplier.get();
        }

        @Override
        public Span start() {
            logger.info("[{}][{}] {}", identifier, name, startLog.get());
            return this;
        }

        @Override
        public void end() {
            logger.info("[{}][{}] Span ended", identifier, name);
        }

        @Override
        public Span recordException(Throwable t) {
            logger.info("[{}][{}] Span recorded exception", identifier, name, t);
            return this;
        }
    }
}
