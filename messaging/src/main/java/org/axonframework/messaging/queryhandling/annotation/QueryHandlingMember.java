/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.queryhandling.annotation;

import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.queryhandling.QueryMessage;

import java.lang.reflect.Type;

/**
 * Interface indicating that a {@link MessageHandlingMember} is capable of handling specific
 * {@link QueryMessage QueryMessages}.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the message.
 * @author Allard Buijze
 * @since 3.1.0
 */
@Internal
public interface QueryHandlingMember<T> extends MessageHandlingMember<T> {

    /**
     * Returns the name of the query the handler can handle.
     *
     * @return The name of the query the handler can handle.
     */
    String queryName();

    /**
     * Returns the return type declared by the handler
     *
     * @return The return type declared by the handler.
     */
    Type resultType();
}
