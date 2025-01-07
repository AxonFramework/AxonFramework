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

package org.axonframework.messaging.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.QualifiedName;

import java.util.Set;

/**
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 * TODO next session -> make a implementation that can be used by any Message Bus type. Or, at least try to.
 */
public interface MessageHandlerRegistry {

    /**
     * @param names   The set of {@link QualifiedName message types} the given {@code messageHandler} can handle.
     * @param messageHandler A {@link MessageHandler handler} or {@link MessageHandlingComponent handling component}
     *                       interested in messages dispatched with any of the given {@code names}.
     * @return This registry for fluent interfacing.
     */
    <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> MessageHandlerRegistry registerMessageHandler(
            @Nonnull Set<QualifiedName> names,
            @Nonnull H messageHandler
    );

    default <H extends MessageHandler<M, R>, M extends Message<?>, R extends Message<?>> MessageHandlerRegistry registerMessageHandler(
            @Nonnull QualifiedName name,
            @Nonnull H messageHandler
    ) {
        return registerMessageHandler(Set.of(name), messageHandler);
    }

    /**
     * Generic registration of a {@link MessageHandlerInterceptor} used for <b>all</b> handlers
     * {@link #registerHandler(Set, org.axonframework.messaging.MessageHandler) registered} in this registry.
     *
     * @param interceptor
     * @return
     */ // TODO implement this in one way or another
//    Registration registerInterceptor(MessageHandlerInterceptor<M, R> interceptor);
}
