/*
 * Copyright (c) 2010-2015. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging;

import org.axonframework.messaging.unitofwork.UnitOfWork;

/**
 * Interface for a component that processes Messages.
 *
 * @param <T> The message type this handler can process
 * @author Rene de Waele
 * @since 3.0
 */
public interface MessageHandler<T extends Message<?>> {

    /**
     * Handles the given <code>message</code>.
     *
     * @param message        The message to be processed.
     * @param unitOfWork     The UnitOfWork the message is processed in
     * @return The result of the message processing.
     *
     * @throws Exception any exception that occurs during message handling
     */
    Object handle(T message, UnitOfWork<? extends T> unitOfWork) throws Exception;

}
