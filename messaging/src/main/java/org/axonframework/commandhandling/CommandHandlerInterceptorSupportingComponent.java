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

package org.axonframework.commandhandling;

import org.axonframework.messaging.MessageHandlerInterceptor;

/**
 * Defines that this component supports the registrations of {@link MessageHandlerInterceptor} instances for
 * {@link CommandMessage}s.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @param <SELF> the type of the implementing class
 */
public interface CommandHandlerInterceptorSupportingComponent<SELF extends CommandHandlerInterceptorSupportingComponent<SELF>> {

    /**
     * Registers the given {@code interceptor} to be invoked when a {@link CommandMessage} is handled by this
     * component.
     *
     * @param interceptor the interceptor to register
     * @return the instance of the implementing class
     */
    SELF registerInterceptor(MessageHandlerInterceptor<? super CommandMessage<?>> interceptor);
}
