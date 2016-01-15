/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.eventhandling.EventMessage;

import java.util.Map;

import static java.lang.String.format;

public interface EntityModel<T> {

    String getIdentifier(T target);

    String routingKey();

    void publish(EventMessage<?> message, T target);

    Map<String, CommandMessageHandler<? super T>> commandHandlers();

    default CommandMessageHandler<? super T> commandHandler(String commandName) {
        CommandMessageHandler<? super T> handler = commandHandlers().get(commandName);
        if (handler == null) {
            throw new NoHandlerForCommandException(format("No handler available to handle command [%s]", commandName));
        }
        return handler;
    }

    <C> EntityModel<C> modelOf(Class<? extends C> childEntityType);
}
