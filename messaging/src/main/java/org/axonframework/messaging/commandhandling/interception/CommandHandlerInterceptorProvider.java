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

package org.axonframework.messaging.commandhandling.interception;

import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.core.MessageHandlerInterceptor;

import java.util.List;

/**
 * Provider for command handler interceptors bound to a specific handler instance.
 *
 * @author Steven van Beelen
 * @since 5.2.0
 */
@Internal
public interface CommandHandlerInterceptorProvider {

    /**
     * Returns the interceptors to apply for this handler instance.
     *
     * @return The list of interceptors for this handler.
     */
    List<MessageHandlerInterceptor<? super CommandMessage>> commandHandlerInterceptors();
}
