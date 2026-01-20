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

package org.axonframework.messaging.core.interception;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MessageDispatchInterceptor} and {@link MessageHandlerInterceptor} implementation that logs dispatched and
 * incoming messages, and their result, to a {@link Logger}.
 * <p>
 * Allows configuration of the name under which the logger should log the statements.
 * <p/>
 * Dispatched, incoming messages and successful executions are logged at the {@code INFO} level. Processing errors are
 * logged using the {@code WARN} level.
 *
 * @param <M> The message type this interceptor can process.
 * @author Allard Buijze
 * @since 0.6.0
 */
public class LoggingInterceptor<M extends Message>
        implements MessageDispatchInterceptor<M>, MessageHandlerInterceptor<M> {

    private final Logger logger;

    /**
     * Initialize the {@code LoggingInterceptor} with the given {@code loggerName}.
     * <p>
     * The actual logging implementation will use this name to decide the appropriate log level and location. See the
     * documentation of your logging implementation for more information.
     *
     * @param loggerName The name of the logger.
     */
    public LoggingInterceptor(String loggerName) {
        this.logger = LoggerFactory.getLogger(loggerName);
    }

    /**
     * Initialize the {@code LoggingInterceptor} with the default logger name, which is the fully qualified class name
     * of this logger.
     *
     * @see LoggingInterceptor#LoggingInterceptor(String)
     */
    public LoggingInterceptor() {
        this.logger = LoggerFactory.getLogger(LoggingInterceptor.class);
    }

    @Override
    @Nonnull
    public MessageStream<?> interceptOnDispatch(@Nonnull M message,
                                                @Nullable ProcessingContext context,
                                                @Nonnull MessageDispatchInterceptorChain<M> interceptorChain) {
        logger.info("Dispatched message: [{}]", message.type().name());
        return interceptorChain.proceed(message, context);
    }

    @Override
    @Nonnull
    public MessageStream<?> interceptOnHandle(@Nonnull M message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<M> interceptorChain) {
        logger.info("Incoming message: [{}]", message.type().name());
        return interceptorChain.proceed(message, context)
                               .map(returnValue -> {
                                   logger.info("[{}] executed successfully with a [{}] return value",
                                               message.type().name(),
                                               returnValue.message().payloadType().getSimpleName());
                                   return returnValue;
                               })
                               .onErrorContinue(e -> {
                                   logger.warn("[{}] resulted in an error",
                                               message.type().name(),
                                               e);
                                   return MessageStream.failed(e);
                               });
    }
}
