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

package org.axonframework.commandhandling;

/**
 * Interceptor that allows commands to be intercepted and modified before they are dispatched by the Command Bus. This
 * interceptor provides a very early means to alter or reject Command Messages, even before any Unit of Work is
 * created.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface CommandDispatchInterceptor {

    /**
     * Invoked each time a command is about to be dispatched on a Command Bus. The given <code>commandMessage</code>
     * represents the command being dispatched.
     *
     * @param commandMessage The command message intended to be dispatched on the Command Bus
     * @return the command message to dispatch on the Command Bus
     */
    CommandMessage<?> handle(CommandMessage<?> commandMessage);
}
