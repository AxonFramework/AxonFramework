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

package org.axonframework.core.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of the CommandBus that dispatches commands to the handlers subscribed to that specific type of
 * command. Interceptors may be configured to add processing to commands regardless of their type, for example logging,
 * security (authorization), sla monitoring, etc.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class SimpleCommandBus implements CommandBus {

    private final ConcurrentMap<Class<?>, CommandHandler<?>> subscriptions = new ConcurrentHashMap<Class<?>, CommandHandler<?>>();
    private volatile InterceptorChain interceptorChain = new InterceptorChain(Collections.<CommandHandlerInterceptor>emptyList());

    @SuppressWarnings({"unchecked"})
    @Override
    public Object dispatch(Object command) {
        CommandHandler handler = subscriptions.get(command.getClass());
        if (handler == null) {
            throw new NoHandlerForCommandException(String.format("No handler was subscribed to commands of type [%s]",
                                                                 command.getClass().getSimpleName()));
        }
        CommandContextImpl context = new CommandContextImpl(command);
        interceptorChain.handle(context, handler);
        if (context.isSuccessful()) {
            return context.getResult();
        } else {
            throw context.getException();
        }
    }

    /**
     * Subscribe the given <code>handler</code> to commands of type <code>commandType</code>. If a subscription already
     * exists for the given type, then the new handler takes over the subscription.
     *
     * @param commandType The type of command to subscribe the handler to
     * @param handler     The handler instance that handles the given type of command
     * @param <T>         The Type of command
     */
    @Override
    public <T> void subscribe(Class<T> commandType, CommandHandler<? super T> handler) {
        subscriptions.put(commandType, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void unsubscribe(Class<T> commandType, CommandHandler<? super T> handler) {
        subscriptions.remove(commandType, handler);
    }

    /**
     * Registers the given list of interceptors to the command bus. All incoming commands will pass throught the
     * interceptors at the given order before the command is passed to the handler for processing. After handling, the
     * <code>afterCommandHandling</code> methods are invoked on the interceptors in the reverse order.
     *
     * @param interceptors The interceptors to invoke when commands are dispatched
     */
    public void setInterceptors(List<? extends CommandHandlerInterceptor> interceptors) {
        this.interceptorChain = new InterceptorChain(interceptors);
    }

    /**
     * Convenience method that allows you to register command handlers using a Dependency Injection framework. The
     * parameter of this method is a <code>Map&lt;Class&lt;T&gt;, CommandHandler&lt;? super T&gt;&gt;</code>. The key
     * represents the type of command to register the handler for, the value is the actual handler.
     *
     * @param handlers The handlers to subscribe in the form of a Map of Class - CommandHandler entries.
     */
    @SuppressWarnings({"unchecked"})
    public void setSubscriptions(Map<?, ?> handlers) {
        for (Map.Entry<?, ?> entry : handlers.entrySet()) {
            subscribe((Class<?>) entry.getKey(), (CommandHandler) entry.getValue());
        }
    }
}
