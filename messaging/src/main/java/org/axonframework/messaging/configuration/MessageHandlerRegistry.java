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

import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.queryhandling.QueryHandlerRegistry;

import java.util.Set;

/**
 * Subscribe the given {@code handler} for messages of the given {@code names}.
 * <p/>
 * If a subscription already exists for any {@link QualifiedName name} in the given set, the behavior is undefined.
 * Implementations may throw an exception to refuse duplicate subscription or alternatively decide whether the existing
 * or new {@code handler} gets the subscription.
 *
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public sealed interface MessageHandlerRegistry permits CommandHandlerRegistry, EventHandlerRegistry,
        QueryHandlerRegistry {

    /**
     * Generic registration of a {@link MessageHandlerInterceptor} used for <b>all</b> handlers
     * {@link #registerHandler(Set, org.axonframework.messaging.MessageHandler) registered} in this registry.
     *
     * @param interceptor
     * @return
     */ // TODO implement this in one way or another
//    Registration registerInterceptor(MessageHandlerInterceptor<M, R> interceptor);
}
