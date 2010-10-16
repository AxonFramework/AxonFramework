/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.interceptors;

import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.InterceptorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;

/**
 * Command Handler Interceptor that logs incoming commands and their result to a SLF4J logger. Allow configuration of
 * the name under which the logger should log the statements.
 * <p/>
 * Incoming commands and successful executions are logged at the <code>INFO</code> level. Processing errors are logged
 * using the <code>WARN</code> level.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public class LoggingInterceptor implements CommandHandlerInterceptor {

    private final Logger logger;

    /**
     * Initialize the LoggingInterceptor with the given <code>loggerName</code>. The actual logging implementation will
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
    public Object handle(Object command, InterceptorChain chain) throws Throwable {
        logger.info("Incoming command: [{}]", command.getClass().getSimpleName());
        try {
            Object returnValue = chain.proceed();
            logger.info("[{}] executed successfully with [{}] return type",
                        command.getClass().getSimpleName(),
                        returnValue == null ? "null" : Void.TYPE.equals(returnValue) ? "void" : returnValue.getClass()
                                .getSimpleName());
            return returnValue;
        } catch (Throwable t) {
            logger.warn(format("[%s] execution failed:", command.getClass().getSimpleName()), t);
            throw t;
        }
    }
}
