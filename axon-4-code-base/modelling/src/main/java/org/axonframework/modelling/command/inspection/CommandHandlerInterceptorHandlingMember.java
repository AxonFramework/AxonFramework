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

package org.axonframework.modelling.command.inspection;

import org.axonframework.messaging.annotation.MessageInterceptingMember;

/**
 * Interface specifying a message handler capable of intercepting a command.
 *
 * @param <T> the type of entity to which the message handler will delegate tha actual interception
 *
 * @author Milan Savic
 * @since 3.3
 * @deprecated in favor of the more generic {@link MessageInterceptingMember}
 */
@Deprecated
public interface CommandHandlerInterceptorHandlingMember<T> extends MessageInterceptingMember<T> {

    /**
     * Indicates whether interceptor chain (containing a command handler) should be invoked automatically or command
     * handler interceptor will invoke it manually.
     *
     * @return {@code true} if interceptor chain should be invoked automatically
     */
    boolean shouldInvokeInterceptorChain();
}
