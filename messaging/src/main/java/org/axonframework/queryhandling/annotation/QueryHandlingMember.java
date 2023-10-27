/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.queryhandling.annotation;

import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.lang.reflect.Type;

/**
 * Interface indicating that a MessageHandlingMember is capable of handling specific query messages.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the message
 * @author Allard Buijze
 * @since 3.1
 */
public interface QueryHandlingMember<T> extends MessageHandlingMember<T> {

    /**
     * Returns the name of the query the handler can handle
     *
     * @return the name of the query the handler can handle
     */
    String getQueryName();

    /**
     * Returns the return type declared by the handler
     *
     * @return the return type declared by the handler
     */
    Type getResultType();
}
