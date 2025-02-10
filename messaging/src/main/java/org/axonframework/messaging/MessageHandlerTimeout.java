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

package org.axonframework.messaging;

import org.axonframework.messaging.annotation.HasHandlerAttributes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the timeout for a message handler. Takes precedence over configuration through
 * {@link org.axonframework.messaging.timeout.HandlerTimeoutConfiguration}.
 * <p>
 * The {@code messageTimeout} attribute specifies the timeout for a single message. The {@code batchTimeout} attribute
 * specifies the timeout for a batch of messages, if applicable. A value of {@code -1} indicates that the default
 * timeout should be used.
 *
 * @author Mitchell Herrijgers
 * @since 4.11
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@HasHandlerAttributes
public @interface MessageHandlerTimeout {

    /**
     * The timeout for a single message. A value of {@code -1} indicates that the default timeout should be used.
     *
     * @return the timeout for a single message handler
     */
    int timeout() default -1;

    /**
     * The time after which a warning log message should be displayed that the message handler is taking too long. A
     * value of {@code -1} indicates that the default warning threshold should be used. A value higher than the timeout
     * will disable warnings.
     *
     * @return the time after which a warning log message should be displayed
     */
    int warningThreshold() default -1;

    /**
     * The interval at which warning log messages should be displayed that the message handler is taking too long. A
     * value of {@code -1} indicates that the default warning interval should be used.
     *
     * @return the interval at which warning log messages should be displayed
     */
    int warningInterval() default -1;
}
