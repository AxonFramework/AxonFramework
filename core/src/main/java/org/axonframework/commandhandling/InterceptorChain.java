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
 * @author Allard Buijze
 */
public interface InterceptorChain {

    /**
     * Process the given <code>context</code> and pass it on to the next item in the chain. If this instance is the last
     * item in the chain, the command is passed to the <code>handler</code> for execution.
     *
     * @param command The command being executed
     * @return The return value of the command execution, if any
     *
     * @throws Throwable any exceptions thrown by interceptors or the command handler
     */
    Object proceed(CommandContext command) throws Throwable;
}
