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

import java.util.Iterator;

/**
 * Mechanism that takes care of interceptor and event handler execution.
 *
 * @author Allard Buijze
 * @since 0.5
 */
class DefaultInterceptorChain implements InterceptorChain {

    private final Object command;
    private final CommandHandler handler;
    private Iterator<? extends CommandHandlerInterceptor> chain;

    public DefaultInterceptorChain(Object command, CommandHandler<?> handler,
                                   Iterable<? extends CommandHandlerInterceptor> chain) {
        this.command = command;
        this.handler = handler;
        this.chain = chain.iterator();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Object proceed(Object command) throws Throwable {
        if (chain.hasNext()) {
            return chain.next().handle(command, this);
        } else {
            return handler.handle(command);
        }
    }

    @Override
    public Object proceed() throws Throwable {
        return proceed(command);
    }
}
