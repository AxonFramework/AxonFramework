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

import java.util.List;

/**
 * Mechanism that takes care of interceptor and event handler execution.
 *
 * @author Allard Buijze
 * @since 0.5
 */
class DefaultInterceptorChain implements InterceptorChain {

    private final InterceptorChain next;
    private final CommandHandlerInterceptor current;

    /**
     * Initialize an InterceptorChain for the given <code>interceptors</code>.
     *
     * @param interceptors The list of interceptors to create the chain for. May not be <code>null</code>
     * @throws NullPointerException if the given list of <code>interceptors</code> is <code>null</code>
     */
    public DefaultInterceptorChain(List<? extends CommandHandlerInterceptor> interceptors) {
        if (interceptors.size() > 0) {
            current = interceptors.get(0);
            next = new DefaultInterceptorChain(interceptors.subList(1, interceptors.size()));
        } else {
            current = null;
            next = null;
        }
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Object proceed(CommandContext commandContext) throws Throwable {
        if (current != null) {
            return current.handle(commandContext, next);
        } else {
            return commandContext.getCommandHandler().handle(commandContext.getCommand(), commandContext);
        }
    }
}
