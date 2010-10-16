/*
 * Copyright (c) 2010. Axon Framework
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
 * The interceptor chain manages the flow of a command through a chain of interceptors and ultimately to the command
 * handler. Interceptors may continue processing via this chain by calling the {@link #proceed()} or {@link
 * #proceed(Object)} methods. Alternatively, they can block processing by returning without calling either of these
 * methods.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface InterceptorChain {

    /**
     * Signals the Interceptor Chain to continue processing the incoming (original) command.
     *
     * @return The return value of the command execution, if any
     *
     * @throws Throwable any exceptions thrown by interceptors or the command handler
     * @since 0.7
     */
    Object proceed() throws Throwable;

    /**
     * Signals the Interceptor Chain to continue processing the given command.
     *
     * @param command The command being executed
     * @return The return value of the command execution, if any
     *
     * @throws Throwable any exceptions thrown by interceptors or the command handler
     */
    Object proceed(Object command) throws Throwable;
}
