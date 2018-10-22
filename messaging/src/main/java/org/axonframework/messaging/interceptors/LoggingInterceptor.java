/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.messaging.interceptors;

import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

/**
 * Message Handler Interceptor that logs incoming messages and their result to a SLF4J logger. Allow configuration of
 * the name under which the logger should log the statements.
 * <p/>
 * Incoming messages and successful executions are logged at the {@code INFO} level. Processing errors are logged
 * using the {@code WARN} level.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class LoggingInterceptor<T extends Message<?>> implements MessageHandlerInterceptor<T> {

    private final Logger logger;

    /**
     * Initialize the LoggingInterceptor with the given {@code loggerName}. The actual logging implementation will
     * use this name to decide the appropriate log level and location. See the documentation of your logging
     * implementation for more information.
     *
     * @param loggerName the name of the logger
     */
    public LoggingInterceptor(String loggerName) {
        this.logger = LoggerFactory.getLogger(loggerName);
    }

    /**
     * Initialize the LoggingInterceptor with the default logger name, which is the fully qualified class name of this
     * logger.
     *
     * @see LoggingInterceptor#LoggingInterceptor(String)
     */
    public LoggingInterceptor() {
        this.logger = LoggerFactory.getLogger(LoggingInterceptor.class);
    }

    @Override
    public Object handle(UnitOfWork<? extends T> unitOfWork, InterceptorChain interceptorChain)
            throws Exception {
        T message = unitOfWork.getMessage();
        logger.info("Incoming message: [{}]", message.getPayloadType().getSimpleName());
        try {
            Object returnValue = interceptorChain.proceed();
            logger.info("[{}] executed successfully with a [{}] return value",
                    message.getPayloadType().getSimpleName(),
                    returnValue == null ? "null" : returnValue.getClass().getSimpleName());
            return returnValue;
        } catch (Exception t) {
            logger.warn(format("[%s] execution failed:", message.getPayloadType().getSimpleName()), t);
            throw t;
        }
    }
}
