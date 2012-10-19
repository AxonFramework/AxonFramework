/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.commandhandling.annotation;

import org.axonframework.common.annotation.AbstractMessageHandler;

/**
 * Utility class that resolves the name of a Command accepted by a given handler.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class CommandMessageHandlerUtils {

    /**
     * Returns the name of the Command accepted by the given <code>handler</code>.
     *
     * @param handler The handler to resolve the name from
     * @return The name of the command accepted by the handler
     */
    public static String resolveAcceptedCommandName(AbstractMessageHandler handler) {
        CommandHandler annotation = handler.getAnnotation(CommandHandler.class);
        if (annotation != null && !"".equals(annotation.commandName())) {
            return annotation.commandName();
        }
        return handler.getPayloadType().getName();
    }
}
