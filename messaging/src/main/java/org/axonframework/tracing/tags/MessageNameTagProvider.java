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

package org.axonframework.tracing.tags;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.tracing.TagProvider;

import java.util.Map;

import static java.util.Collections.singletonMap;

public class MessageNameTagProvider implements TagProvider {

    @Override
    public Map<String, String> provideForMessage(Message<?> message) {
        String name = determineName(message);
        if (name != null) {
            return singletonMap("axon.message-name", message.getIdentifier());
        }
        return null;
    }

    private String determineName(Message<?> message) {
        if (message instanceof CommandMessage) {
            return ((CommandMessage<?>) message).getCommandName();
        }
        if (message instanceof QueryMessage) {
            return ((QueryMessage<?, ?>) message).getQueryName();
        }
        return null;
    }
}
