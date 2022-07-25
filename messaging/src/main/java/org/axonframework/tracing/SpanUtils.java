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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.QueryMessage;

import java.util.Objects;

/**
 * Utilities for creating spans which are relevant for all implementations of tracing.
 */
public class SpanUtils {

    private SpanUtils() {
        // For sonar
    }

    /**
     * Creates a human-readable name for a message's payload. This is the simple name of the payload by default, unless
     * the more specific name of a message, is different from it. This can be the case for commands and queries.
     *
     * @param message The message to determine a message name for
     * @return The message's name
     */
    public static String determineMessageName(Message<?> message) {
        String messageName = message.getPayloadType().getSimpleName();
        if (message instanceof CommandMessage) {
            String commandName = ((CommandMessage<?>) message).getCommandName();
            if (!Objects.equals(commandName, message.getPayloadType().getName())) {
                messageName = commandName;
            }
        }
        if (message instanceof QueryMessage) {
            String queryName = ((QueryMessage<?, ?>) message).getQueryName();
            if (!Objects.equals(queryName, message.getPayloadType().getName())) {
                messageName = queryName;
            }
        }
        return messageName;
    }
}
