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

package org.axonframework.eventhandling.interceptors;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Message Dispatch Interceptor that logs published events to a SLF4J logger. It does not alter or reject events.
 * <p/>
 * The interceptor also logs the cause of the published events. E.g. if the cause of the events is a command, the
 * interceptor also logs the command and the execution result of the command.
 * <p/>
 * The interceptor allows for configuration of the name under which the logger should log the statements. All logging
 * is done at the {@code INFO} level.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class EventLoggingInterceptor implements MessageDispatchInterceptor<EventMessage<?>> {

    private final Logger logger;

    /**
     * Initialize the LoggingInterceptor with the default logger name, which is the fully qualified class name of this
     * logger.
     *
     * @see EventLoggingInterceptor#EventLoggingInterceptor(String)
     */
    public EventLoggingInterceptor() {
        this(EventLoggingInterceptor.class.getName());
    }

    /**
     * Initialize the interceptor with the given {@code loggerName}. The actual logging implementation will
     * use this name to decide the appropriate log level and location. See the documentation of your logging
     * implementation for more information.
     *
     * @param loggerName the name of the logger
     */
    public EventLoggingInterceptor(String loggerName) {
        this.logger = LoggerFactory.getLogger(loggerName);
    }

    @Nonnull
    @Override
    public BiFunction<Integer, EventMessage<?>, EventMessage<?>> handle(
            @Nonnull List<? extends EventMessage<?>> messages) {
        StringBuilder sb = new StringBuilder(String.format("Events published: [%s]",
                                                           messages.stream()
                                                                   .map(m -> m.getPayloadType().getSimpleName())
                                                                   .collect(Collectors.joining(", "))));
        CurrentUnitOfWork.ifStarted(unitOfWork -> {
            Message<?> message = unitOfWork.getMessage();
            if (message == null) {
                sb.append(" while processing an operation not tied to an incoming message");
            } else {
                sb.append(String.format(" while processing a [%s]", message.getPayloadType().getSimpleName()));
            }
            ExecutionResult executionResult = unitOfWork.getExecutionResult();
            if (executionResult != null) {
                if (executionResult.isExceptionResult()) {
                    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
                    Throwable exception = executionResult.getExceptionResult();
                    exception = exception instanceof ExecutionException ? exception.getCause() : exception;
                    sb.append(String.format(" which failed with a [%s]", exception.getClass().getSimpleName()));
                } else if (executionResult.getResult() != null) {
                    sb.append(String.format(" which yielded a [%s] return value",
                                            executionResult.getResult().getClass().getSimpleName()));
                }
            }
        });
        logger.info(sb.toString());
        return (i, m) -> m;
    }
}
