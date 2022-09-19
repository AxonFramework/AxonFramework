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

package org.axonframework.queryhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the QueryInvocationErrorHandler that logs all errors to a given {@link Logger}.
 *
 * @author Allard Buijze
 * @since 3.1
 */
public class LoggingQueryInvocationErrorHandler implements QueryInvocationErrorHandler {

    private final Logger logger;

    /**
     * Instantiate a {@link LoggingQueryInvocationErrorHandler} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link LoggingQueryInvocationErrorHandler} instance
     */
    protected LoggingQueryInvocationErrorHandler(Builder builder) {
        builder.validate();
        this.logger = builder.logger;
    }

    /**
     * Instantiate a Builder to be able to create a {@link LoggingQueryInvocationErrorHandler}.
     * <p>
     * The {@link Logger} is defaulted to a {@link LoggerFactory#getLogger(Class)} call using {@code
     * LoggingQueryInvocationErrorHandler.class}.
     *
     * @return a Builder to be able to create a {@link LoggingQueryInvocationErrorHandler}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void onError(@Nonnull Throwable error, @Nonnull QueryMessage<?, ?> queryMessage,
                        @Nonnull MessageHandler messageHandler) {
        logger.warn("An error occurred while processing query message [{}]", queryMessage.getQueryName(), error);
    }

    /**
     * Builder class to instantiate a {@link LoggingQueryInvocationErrorHandler}.
     * <p>
     * The {@link Logger} is defaulted to a {@link LoggerFactory#getLogger(Class)} call using {@code
     * LoggingQueryInvocationErrorHandler.class}.
     */
    public static class Builder {

        private Logger logger = LoggerFactory.getLogger(LoggingQueryInvocationErrorHandler.class);

        /**
         * Sets the {@link Logger} to log errors with from query handlers. Defaults to the result of {@link
         * LoggerFactory#getLogger(Class)}, using {@code LoggingQueryInvocationErrorHandler.class} as input. Defaults to
         * a {@link LoggerFactory#getLogger(Class)} call, providing the {@link LoggingQueryInvocationErrorHandler}
         * type.
         *
         * @param logger the {@link Logger} to log errors with from query handlers
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder logger(Logger logger) {
            assertNonNull(logger, "Logger may not be null");
            this.logger = logger;
            return this;
        }

        /**
         * Initializes a {@link LoggingQueryInvocationErrorHandler} as specified through this Builder.
         *
         * @return a {@link LoggingQueryInvocationErrorHandler} as specified through this Builder
         */
        public LoggingQueryInvocationErrorHandler build() {
            return new LoggingQueryInvocationErrorHandler(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            // Method kept for overriding
        }
    }
}
