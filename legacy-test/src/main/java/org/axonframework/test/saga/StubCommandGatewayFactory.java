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

package org.axonframework.test.saga;

import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.Message;
import org.axonframework.test.FixtureExecutionException;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Creates the gateway proxies handed out by {@link FixtureConfiguration#registerCommandGateway(Class)}.
 * <p>
 * Every call on the proxy dispatches its first argument as a command, so the fixture records it and
 * {@link FixtureExecutionResult#expectDispatchedCommands(Object...)} sees it. What the call returns follows Axon
 * Framework 4: the stub implementation decides when one was given, and otherwise the dispatch result is returned if it
 * has already arrived, {@code null} if it has not.
 * <p>
 * This is a deliberate subset of Axon Framework 4's {@code CommandGatewayFactory}. It does not support timeouts,
 * declared or as a parameter, a retry scheduler, {@code @MetaDataValue} parameters or callback parameters. A method
 * taking more than the command is dispatched on its first argument alone, and the rest reach the stub, if there is
 * one, and nothing else.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
final class StubCommandGatewayFactory {

    private StubCommandGatewayFactory() {
        // Utility class, not meant to be instantiated.
    }

    /**
     * Creates a proxy implementing the given {@code gatewayInterface}.
     *
     * @param gatewayInterface   the interface to implement
     * @param stubImplementation the implementation deciding what each call returns, or {@code null} to answer from the
     *                           dispatch result
     * @param commandGateway     supplies the gateway to dispatch on, resolved on first use because the proxy is handed
     *                           out before the fixture starts its configuration
     * @param <I>                the type of gateway to create
     * @return a proxy implementing the given {@code gatewayInterface}
     */
    @SuppressWarnings("unchecked")
    static <I> I createGateway(Class<I> gatewayInterface,
                               @Nullable I stubImplementation,
                               Supplier<CommandGateway> commandGateway) {
        Objects.requireNonNull(gatewayInterface, "The gatewayInterface may not be null.");
        Objects.requireNonNull(commandGateway, "The commandGateway may not be null.");
        if (!gatewayInterface.isInterface()) {
            throw new FixtureExecutionException(
                    "Cannot create a command gateway for [" + gatewayInterface.getName() + "]: it is not an interface."
            );
        }
        return (I) Proxy.newProxyInstance(
                gatewayInterface.getClassLoader(),
                new Class<?>[]{gatewayInterface},
                new DispatchingInvocationHandler(gatewayInterface, stubImplementation, commandGateway)
        );
    }

    private record DispatchingInvocationHandler(
            Class<?> gatewayInterface,
            @Nullable Object stubImplementation,
            Supplier<CommandGateway> commandGateway
    ) implements InvocationHandler {

        @Override
        public @Nullable Object invoke(Object proxy, Method method, Object @Nullable [] args) throws Throwable {
            Object answered = answerFromObjectMethod(proxy, method, args);
            if (answered != NOT_AN_OBJECT_METHOD) {
                return answered;
            }
            if (args == null || args.length == 0) {
                throw new FixtureExecutionException(
                        "Cannot dispatch a command for [" + gatewayInterface.getName() + "#" + method.getName()
                                + "]: a gateway method needs at least one parameter, the command to dispatch."
                );
            }

            CompletableFuture<? extends Message> result = commandGateway.get()
                                                                        .send(args[0])
                                                                        .getResultMessage();
            if (stubImplementation != null) {
                try {
                    return method.invoke(stubImplementation, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            if (method.getReturnType().equals(CompletableFuture.class)) {
                return result.thenApply(Message::payload);
            }
            if (method.getReturnType().equals(void.class) || method.getReturnType().equals(Void.class)) {
                return null;
            }
            // As in Axon Framework 4: answer only if the result is already there. Without a callback behaviour or a
            // subscribed handler there is nothing to wait for, and blocking would deadlock the fixture's own thread.
            if (result.isDone() && !result.isCompletedExceptionally()) {
                Message message = result.getNow(null);
                return message == null ? null : message.payload();
            }
            return null;
        }

        private static final Object NOT_AN_OBJECT_METHOD = new Object();

        /**
         * Answers {@link Object#equals(Object)}, {@link Object#hashCode()} and {@link Object#toString()} on the proxy
         * itself. Dispatching those as commands would be nonsense, and a debugger inspecting the gateway would do it.
         */
        private Object answerFromObjectMethod(Object proxy, Method method, Object @Nullable [] args) {
            return switch (method.getName()) {
                case "equals" -> args != null && args.length == 1 && proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "StubCommandGateway[" + gatewayInterface.getName() + "]";
                default -> NOT_AN_OBJECT_METHOD;
            };
        }
    }
}
