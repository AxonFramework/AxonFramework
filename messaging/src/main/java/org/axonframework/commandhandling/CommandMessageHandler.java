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

package org.axonframework.commandhandling;

import org.axonframework.messaging.MessageHandler;

import java.util.Set;

/**
 * MessageHandler specialization for handlers of Command Messages. Besides handling a message, CommandMessageHandlers
 * also specify which command names they support.
 */
public interface CommandMessageHandler extends MessageHandler<CommandMessage<?>> {

    /**
     * Returns the set of command names this handler supports.
     *
     * @return the set of supported command names
     */
    Set<String> supportedCommandNames();

}
