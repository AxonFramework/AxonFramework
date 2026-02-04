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

package org.axonframework.modelling.entity.annotation;

import org.axonframework.messaging.commandhandling.annotation.RoutingKey;

/**
 * Exception indicating that a child entity indicated a routing key that is not known on the incoming message. As such,
 * a child entity could not be resolved to handle the message.
 * <p>
 * This issue can be resolved by ensuring the routing key specified on the child entity matches the routing key
 * specified on the incoming message. Ensure that the {@link EntityMember#routingKey} points to a valid member of the
 * message, or in its absence that the name of the
 * {@link RoutingKey}-annotated member on the child entity does.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class UnknownRoutingKeyException extends RuntimeException {

    /**
     * Creates a new exception with the given {@code message}.
     *
     * @param message The message describing the cause of the exception.
     */
    public UnknownRoutingKeyException(String message) {
        super(message);
    }
}
