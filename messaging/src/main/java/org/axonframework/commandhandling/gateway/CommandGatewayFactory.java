/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.responsetypes.ResponseType;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

/**
 * Factory that creates Gateway implementations from custom interface definitions. The behavior of the method is defined
 * by the parameters, declared exceptions and return type of the method.
 * <p/>
 * <em>Supported parameter types:</em><ul> <li>The first parameter of the method is considered the payload of the
 * message. If the first parameter is a Message itself, a new message is created using the payload and metadata of the
 * message passed as parameter.</li> <li>Parameters that are annotated with {@link MetaDataValue @MetaDataValue} will
 * cause the parameter values to be added as meta data values to the outgoing message.</li> <li>If the last two
 * parameters are of type {@link Long long} and {@link TimeUnit}, they are considered to represent the timeout for the
 * command. The method will block for as long as the command requires to execute, or until the timeout expires.</li>
 * </ul>
 * <p/>
 * <em>Effect of return values</em><ul> <li>{@code void} return types are always allowed. Unless another parameter makes
 * the method blocking, void methods are non-blocking by default.</li> <li>Declaring a {@link Future} return type will
 * always result in a non-blocking operation. A future is returned that allows you to retrieve the execution's result at
 * your own convenience. Note that declared exceptions and timeouts are ignored.</li> <li>Any other return type will
 * cause the dispatch to block (optionally with timeout) until a result is available</li> </ul>
 * <p/>
 * <em>Effect of declared exceptions</em> <ul> <li>Any checked exception declared on the method will cause it to block
 * (optionally with timeout). If the command results in a declared checked exception, that exception is thrown from the
 * method.</li> <li>Declaring a {@link TimeoutException} will throw that exception when a configured timeout expires. If
 * no such exception is declared, but a timeout is configured, the method will return {@code null}.</li> <li>Declaring
 * an {@link InterruptedException} will throw that exception when a thread blocked while waiting for a response is
 * interrupted. Not declaring the exception will have the method return {@code null} when a blocked thread is
 * interrupted. Note that when no InterruptedException is declared, the interrupt flag is set back on the interrupted
 * thread</li> </ul>
 * <p/>
 * <em>Effect of unchecked exceptions</em> <ul> <li>Any unchecked exception thrown during command handling will cause it
 * to block. If the method is blocking (see below) the unchecked exception will be thrown from the method</li> </ul>
 * <p/>
 * Finally, the {@link Timeout @Timeout} annotation can be used to define a timeout on a method. This will always cause
 * a method invocation to block until a response is available, or the timeout expires.
 * <p/>
 * Any method will be blocking if: <ul> <li>It declares a return type other than {@code void} or {@code Future}, or</li>
 * <li>It declares an exception, or</li> <li>The last two parameters are of type {@link TimeUnit} and {@link Long long},
 * or</li> <li>The method is annotated with {@link Timeout @Timeout}</li> </ul> In other cases, the method is
 * non-blocking and will return immediately after dispatching a command.
 * <p/>
 * This factory is thread safe once configured, and so are the gateways it creates.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CommandGatewayFactory {

    private final CommandBus commandBus;
    private final RetryScheduler retryScheduler;
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors;

    private final List<CommandCallback<?, ?>> commandCallbacks;

    /**
     * Instantiate a {@link CommandGatewayFactory} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link CommandBus} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link CommandGatewayFactory} instance
     */
    protected CommandGatewayFactory(Builder builder) {
        builder.validate();
        this.commandBus = builder.commandBus;
        this.retryScheduler = builder.retryScheduler;
        if (builder.dispatchInterceptors != null && !builder.dispatchInterceptors.isEmpty()) {
            this.dispatchInterceptors = new CopyOnWriteArrayList<>(builder.dispatchInterceptors);
        } else {
            this.dispatchInterceptors = new CopyOnWriteArrayList<>();
        }
        this.commandCallbacks = new CopyOnWriteArrayList<>();
    }

    /**
     * Instantiate a Builder to be able to create a {@link CommandGatewayFactory}.
     * <p>
     * The {@link CommandBus} is a <b>hard requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link CommandGatewayFactory}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a gateway instance for the given {@code gatewayInterface}. The returned instance is a Proxy that
     * implements that interface.
     *
     * @param gatewayInterface The interface declaring the gateway methods
     * @param <T>              The interface declaring the gateway methods
     * @return A Proxy implementation implementing the given interface
     */
    @SuppressWarnings("unchecked")
    public <T> T createGateway(Class<T> gatewayInterface) {
        Map<Method, InvocationHandler> dispatchers = new HashMap<>();
        for (Method gatewayMethod : gatewayInterface.getMethods()) {
            MetaDataExtractor[] extractors = extractMetaData(gatewayMethod.getParameters());

            final Class<?>[] arguments = gatewayMethod.getParameterTypes();

            InvocationHandler dispatcher = DispatchOnInvocationHandler.builder()
                                                                      .commandBus(commandBus)
                                                                      .retryScheduler(retryScheduler)
                                                                      .dispatchInterceptors(dispatchInterceptors)
                                                                      .metaDataExtractors(extractors)
                                                                      .commandCallbacks(commandCallbacks)
                                                                      .forceCallbacks(true)
                                                                      .build();

            if (!Arrays.asList(CompletableFuture.class, Future.class, CompletionStage.class)
                       .contains(gatewayMethod.getReturnType())) {

                if (arguments.length >= 3 && TimeUnit.class.isAssignableFrom(arguments[arguments.length - 1]) &&
                        (Long.TYPE.isAssignableFrom(arguments[arguments.length - 2]) ||
                                Integer.TYPE.isAssignableFrom(arguments[arguments.length - 2]))) {
                    dispatcher =
                            wrapToReturnWithTimeoutInArguments(dispatcher, arguments.length - 2, arguments.length - 1);
                } else {
                    Map<String, Object> timeout =
                            findAnnotationAttributes(gatewayMethod, Timeout.class).orElse(
                                    findAnnotationAttributes(gatewayMethod.getDeclaringClass(), Timeout.class)
                                            .orElse(null)
                            );
                    if (timeout != null) {
                        dispatcher = wrapToReturnWithFixedTimeout(dispatcher, (int) timeout.get("timeout"),
                                                                  (TimeUnit) timeout.get("unit"));
                    } else if (!Void.TYPE.equals(gatewayMethod.getReturnType()) ||
                            gatewayMethod.getExceptionTypes().length > 0) {
                        dispatcher = wrapToWaitForResult(dispatcher);
                    } else if (commandCallbacks.isEmpty() && !hasCallbackParameters(gatewayMethod)) {
                        // switch to fire-and-forget mode
                        DispatchOnInvocationHandler fireAndForgetHandler =
                                DispatchOnInvocationHandler.builder()
                                                           .commandBus(commandBus)
                                                           .retryScheduler(retryScheduler)
                                                           .dispatchInterceptors(dispatchInterceptors)
                                                           .metaDataExtractors(extractors)
                                                           .commandCallbacks(commandCallbacks)
                                                           .forceCallbacks(false)
                                                           .build();
                        dispatcher = wrapToFireAndForget(fireAndForgetHandler);
                    }
                }
                Class<?>[] declaredExceptions = gatewayMethod.getExceptionTypes();
                if (!contains(declaredExceptions, TimeoutException.class)) {
                    dispatcher = wrapToReturnNullOnTimeout(dispatcher);
                }
                if (!contains(declaredExceptions, InterruptedException.class)) {
                    dispatcher = wrapToReturnNullOnInterrupted(dispatcher);
                }
                dispatcher = wrapUndeclaredExceptions(dispatcher, declaredExceptions);
            }
            dispatchers.put(gatewayMethod, dispatcher);
        }

        GatewayInvocationHandler gatewayInvocationHandler =
                GatewayInvocationHandler.builder()
                                        .commandBus(commandBus)
                                        .retryScheduler(retryScheduler)
                                        .dispatchInterceptors(dispatchInterceptors)
                                        .dispatchers(dispatchers)
                                        .build();
        return gatewayInterface.cast(Proxy.newProxyInstance(
                gatewayInterface.getClassLoader(), new Class[]{gatewayInterface}, gatewayInvocationHandler
        ));
    }

    private boolean hasCallbackParameters(Method gatewayMethod) {
        for (Class<?> parameter : gatewayMethod.getParameterTypes()) {
            if (CommandCallback.class.isAssignableFrom(parameter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wraps the given {@code delegate} in an InvocationHandler that wraps exceptions not declared on the method
     * in a {@link org.axonframework.commandhandling.CommandExecutionException}.
     *
     * @param delegate           The delegate to invoke that potentially throws exceptions
     * @param declaredExceptions The exceptions declared on the method signature
     * @param <R>                The response type of the command handler
     * @return an InvocationHandler that wraps undeclared exceptions in a {@code CommandExecutionException}
     */
    protected <R> InvocationHandler<R> wrapUndeclaredExceptions(final InvocationHandler<R> delegate,
                                                                final Class<?>[] declaredExceptions) {
        return new WrapNonDeclaredCheckedExceptions<>(delegate, declaredExceptions);
    }

    /**
     * Wrap the given {@code delegate} in an InvocationHandler that returns null when the
     * {@code delegate}
     * throws an InterruptedException.
     *
     * @param delegate The delegate to invoke, potentially throwing an InterruptedException when invoked
     * @param <R>      The response type of the command handler
     * @return an InvocationHandler that wraps returns null when an InterruptedException is thrown
     */
    protected <R> InvocationHandler<R> wrapToReturnNullOnInterrupted(final InvocationHandler<R> delegate) {
        return new NullOnInterrupted<>(delegate);
    }

    /**
     * Wrap the given {@code delegate} in an InvocationHandler that returns null when the
     * {@code delegate} throws a TimeoutException.
     *
     * @param delegate The delegate to invoke, potentially throwing a TimeoutException when invoked
     * @param <R>      The response type of the command handler
     * @return an InvocationHandler that wraps returns null when a TimeoutException is thrown
     */
    protected <R> InvocationHandler<R> wrapToReturnNullOnTimeout(final InvocationHandler<R> delegate) {
        return new NullOnTimeout<>(delegate);
    }

    /**
     * Wrap the given {@code delegate} in an InvocationHandler that returns immediately after invoking the
     * {@code delegate}.
     *
     * @param delegate The delegate to invoke, potentially throwing an InterruptedException when invoked
     * @param <R>      The response type of the command handler
     * @return an InvocationHandler that wraps returns immediately after invoking the delegate
     */
    protected <R> InvocationHandler<R> wrapToFireAndForget(final InvocationHandler<CompletableFuture<R>> delegate) {
        return new FireAndForget<>(delegate);
    }

    /**
     * Wraps the given {@code delegate} and waits for the result in the Future to become available. No explicit
     * timeout is provided for the waiting.
     *
     * @param delegate The delegate to invoke, returning a Future
     * @param <R>      The result of the command handler
     * @return the result of the Future, either a return value or an exception
     */
    protected <R> InvocationHandler<R> wrapToWaitForResult(final InvocationHandler<CompletableFuture<R>> delegate) {
        return new WaitForResult<>(delegate);
    }

    /**
     * Wraps the given {@code delegate} and waits for the result in the Future to become available, with given
     * {@code timeout} and {@code timeUnit}.
     *
     * @param delegate The delegate to invoke, returning a Future
     * @param timeout  The amount of time to wait for the result to become available
     * @param timeUnit The unit of time to wait
     * @param <R>      The result of the command handler
     * @return the result of the Future, either a return value or an exception
     */
    protected <R> InvocationHandler<R> wrapToReturnWithFixedTimeout(InvocationHandler<CompletableFuture<R>> delegate,
                                                                    long timeout, TimeUnit timeUnit) {
        return new WaitForResultWithFixedTimeout<>(delegate, timeout, timeUnit);
    }

    /**
     * Wraps the given {@code delegate} and waits for the result in the Future to become available using given
     * indices to resolve the parameters that provide the timeout to use.
     *
     * @param delegate      The delegate to invoke, returning a Future
     * @param timeoutIndex  The index of the argument providing the timeout
     * @param timeUnitIndex The index of the argument providing the time unit
     * @param <R>           The result of the command handler
     * @return the result of the Future, either a return value or an exception
     */
    protected <R> InvocationHandler<R> wrapToReturnWithTimeoutInArguments(
            final InvocationHandler<CompletableFuture<R>> delegate, int timeoutIndex, int timeUnitIndex) {
        return new WaitForResultWithTimeoutInArguments<>(delegate, timeoutIndex, timeUnitIndex);
    }

    private boolean contains(Class<?>[] declaredExceptions, Class<?> exceptionClass) {
        for (Class<?> declaredException : declaredExceptions) {
            if (declaredException.isAssignableFrom(exceptionClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers the {@code callback}, which is invoked for each sent command, unless Axon is able to detect that
     * the result of the command does not match the type accepted by the callback.
     * <p/>
     * Axon will check the signature of the onResult() method and only invoke the callback if the actual result of the
     * command is an instance of that type. If Axon is unable to detect the type, the callback is always invoked,
     * potentially causing {@link java.lang.ClassCastException}.
     *
     * @param callback     The callback to register
     * @param responseType The actual response type of the command
     * @param <R>          The type of return value the callback is interested in
     * @return this instance for further configuration
     */
    public <C, R> CommandGatewayFactory registerCommandCallback(CommandCallback<C, R> callback,
                                                                ResponseType<R> responseType) {
        this.commandCallbacks.add(new TypeSafeCallbackWrapper<>(callback, responseType));
        return this;
    }

    /**
     * Registers the given {@code dispatchInterceptor} which is invoked for each Command dispatched through the
     * Command Gateways created by this factory.
     *
     * @param dispatchInterceptor The interceptor to register.
     * @return this instance for further configuration
     */
    public CommandGatewayFactory registerDispatchInterceptor(
            MessageDispatchInterceptor<CommandMessage<?>> dispatchInterceptor) {
        this.dispatchInterceptors.add(dispatchInterceptor);
        return this;
    }

    private MetaDataExtractor[] extractMetaData(Parameter[] parameters) {
        List<MetaDataExtractor> extractors = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            if (org.axonframework.messaging.MetaData.class.isAssignableFrom(parameters[i].getType())) {
                extractors.add(new MetaDataExtractor(i, null));
            } else {
                Optional<Map<String, Object>> metaDataAnnotation =
                        findAnnotationAttributes(parameters[i], MetaDataValue.class);
                if (metaDataAnnotation.isPresent()) {
                    extractors.add(new MetaDataExtractor(i, (String) metaDataAnnotation.get().get("metaDataValue")));
                }
            }
        }
        return extractors.toArray(new MetaDataExtractor[0]);
    }

    /**
     * Interface towards the mechanism that handles a method call on a gateway interface method.
     *
     * @param <R> The return type of the method invocation
     */
    public interface InvocationHandler<R> {

        /**
         * Handle the invocation of the given {@code invokedMethod}, invoked on given {@code proxy} with
         * given
         * {@code args}.
         *
         * @param proxy         The proxy on which the method was invoked
         * @param invokedMethod The method being invoked
         * @param args          The arguments of the invocation
         * @return the return value of the invocation
         *
         * @throws Exception any exceptions that occurred while processing the invocation
         */
        R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception;
    }

    private static class GatewayInvocationHandler extends AbstractCommandGateway
            implements java.lang.reflect.InvocationHandler {

        private final Map<Method, InvocationHandler> dispatchers;

        protected GatewayInvocationHandler(Builder builder) {
            super(builder);
            this.dispatchers = builder.dispatchers;
        }

        /**
         * Instantiate a Builder to be able to create a {@link GatewayInvocationHandler}.
         * <p>
         * The {@code dispatchInterceptors} are defaulted to an empty list.
         * The {@link CommandBus} is a <b>hard requirements</b> and as such should be provided.
         *
         * @return a Builder to be able to create a {@link GatewayInvocationHandler}
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
            } else {
                final InvocationHandler invocationHandler = dispatchers.get(method);
                return invocationHandler.invoke(proxy, method, args);
            }
        }

        private static class Builder extends AbstractCommandGateway.Builder {

            private Map<Method, InvocationHandler> dispatchers;

            @Override
            public Builder commandBus(CommandBus commandBus) {
                super.commandBus(commandBus);
                return this;
            }

            @Override
            public Builder retryScheduler(RetryScheduler retryScheduler) {
                super.retryScheduler(retryScheduler);
                return this;
            }

            @Override
            public Builder dispatchInterceptors(
                    MessageDispatchInterceptor<? super CommandMessage<?>>... dispatchInterceptors) {
                super.dispatchInterceptors(dispatchInterceptors);
                return this;
            }

            @Override
            public Builder dispatchInterceptors(
                    List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors) {
                super.dispatchInterceptors(dispatchInterceptors);
                return this;
            }

            /**
             * Sets the {@code dispatchers} containing the methods to delegate commands to.
             *
             * @param dispatchers {@code dispatchers} containing the methods to delegate commands to
             * @return the current Builder instance, for fluent interfacing
             */
            public Builder dispatchers(Map<Method, InvocationHandler> dispatchers) {
                this.dispatchers = new HashMap<>(dispatchers);
                return this;
            }

            /**
             * Initializes a {@link GatewayInvocationHandler} as specified through this Builder.
             *
             * @return a {@link GatewayInvocationHandler} as specified through this Builder
             */
            public GatewayInvocationHandler build() {
                return new GatewayInvocationHandler(this);
            }
        }
    }

    private static class DispatchOnInvocationHandler<C, R> extends AbstractCommandGateway
            implements InvocationHandler<CompletableFuture<R>> {

        private final MetaDataExtractor[] metaDataExtractors;
        private final List<CommandCallback<? super C, ? super R>> commandCallbacks;
        private final boolean forceCallbacks;

        @SuppressWarnings("unchecked")
        protected DispatchOnInvocationHandler(Builder builder) {
            super(builder);
            this.metaDataExtractors = builder.metaDataExtractors; // NOSONAR
            this.commandCallbacks = builder.commandCallbacks;
            this.forceCallbacks = builder.forceCallbacks;
        }

        /**
         * Instantiate a Builder to be able to create a {@link DispatchOnInvocationHandler}.
         * <p>
         * The {@code dispatchInterceptors} are defaulted to an empty list.
         * The {@link CommandBus} is a <b>hard requirements</b> and as such should be provided.
         *
         * @return a Builder to be able to create a {@link DispatchOnInvocationHandler}
         */
        public static Builder builder() {
            return new Builder();
        }

        @SuppressWarnings("unchecked")
        @Override
        public CompletableFuture<R> invoke(Object proxy, Method invokedMethod, Object[] args) {
            Object command = args[0];
            if (metaDataExtractors.length != 0) {
                Map<String, Object> metaDataValues = new HashMap<>();
                for (MetaDataExtractor extractor : metaDataExtractors) {
                    extractor.addMetaData(args, metaDataValues);
                }
                if (!metaDataValues.isEmpty()) {
                    command = asCommandMessage(command).withMetaData(metaDataValues);
                }
            }
            if (forceCallbacks || !commandCallbacks.isEmpty()) {
                List<CommandCallback<? super C, ? super R>> callbacks = new LinkedList<>();
                FutureCallback<C, R> future = new FutureCallback<>();
                callbacks.add(future);
                for (Object arg : args) {
                    if (arg instanceof CommandCallback) {
                        final CommandCallback<C, R> callback = (CommandCallback<C, R>) arg;
                        callbacks.add(callback);
                    }
                }
                callbacks.addAll(commandCallbacks);
                send(command, new CompositeCallback(callbacks));
                return future.thenApply(Message::getPayload);
            } else {
                sendAndForget(command);
                return null;
            }
        }

        private static class Builder<C, R> extends AbstractCommandGateway.Builder {

            private MetaDataExtractor[] metaDataExtractors;
            private List<CommandCallback<? super C, ? super R>> commandCallbacks;
            private boolean forceCallbacks;

            @Override
            public Builder commandBus(CommandBus commandBus) {
                super.commandBus(commandBus);
                return this;
            }

            @Override
            public Builder retryScheduler(RetryScheduler retryScheduler) {
                super.retryScheduler(retryScheduler);
                return this;
            }

            @Override
            public Builder dispatchInterceptors(
                    MessageDispatchInterceptor<? super CommandMessage<?>>... dispatchInterceptors) {
                super.dispatchInterceptors(dispatchInterceptors);
                return this;
            }

            @Override
            public Builder dispatchInterceptors(
                    List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors) {
                super.dispatchInterceptors(dispatchInterceptors);
                return this;
            }

            /**
             * Sets the {@code metaDataExtractors}, used to extract any MetaData parameters and store them in the
             * {@link CommandMessage}.
             *
             * @param metaDataExtractors an array of {@link MetaDataExtractor}
             * @return the current Builder instance, for fluent interfacing
             */
            public Builder metaDataExtractors(MetaDataExtractor[] metaDataExtractors) {
                this.metaDataExtractors = metaDataExtractors;
                return this;
            }

            /**
             * Sets the {@code commandCallbacks}, a {@link List} of type {@link CommandCallback}, which are called upon
             * success and failure of handling a command.
             *
             * @param commandCallbacks the {@code commandCallbacks} which are called upon success and failure of
             *                         handling a command
             * @return the current Builder instance, for fluent interfacing
             */
            public Builder commandCallbacks(List<CommandCallback<? super C, ? super R>> commandCallbacks) {
                this.commandCallbacks = commandCallbacks;
                return this;
            }

            /**
             * Toggles {@code forceCallbacks}, which will force a {@link CommandCallback} to be hit after handling a
             * command.
             *
             * @param forceCallbacks a {@code boolean} specifying whether {@link CommandCallback}s should be made
             * @return the current Builder instance, for fluent interfacing
             */
            public Builder forceCallbacks(boolean forceCallbacks) {
                this.forceCallbacks = forceCallbacks;
                return this;
            }

            /**
             * Initializes a {@link DispatchOnInvocationHandler} as specified through this Builder.
             *
             * @return a {@link DispatchOnInvocationHandler} as specified through this Builder
             */
            public DispatchOnInvocationHandler build() {
                return new DispatchOnInvocationHandler(this);
            }
        }
    }

    private static class CompositeCallback<C, R> implements CommandCallback<C, R> {

        private final List<CommandCallback<? super C, ? super R>> callbacks;

        @SuppressWarnings("unchecked")
        public CompositeCallback(List<CommandCallback<? super C, ? super R>> callbacks) {
            this.callbacks = new ArrayList<>(callbacks);
        }

        @Override
        public void onResult(CommandMessage<? extends C> commandMessage,
                             CommandResultMessage<? extends R> commandResultMessage) {
            for (CommandCallback<? super C, ? super R> callback : callbacks) {
                callback.onResult(commandMessage, commandResultMessage);
            }
        }
    }

    private static final class WrapNonDeclaredCheckedExceptions<R> implements InvocationHandler<R> {

        private final Class<?>[] declaredExceptions;
        private final InvocationHandler<R> delegate;

        private WrapNonDeclaredCheckedExceptions(InvocationHandler<R> delegate, Class<?>[] declaredExceptions) {
            this.delegate = delegate;
            this.declaredExceptions = declaredExceptions; // NOSONAR
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception {
            try {
                return delegate.invoke(proxy, invokedMethod, args);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                for (Class<?> exception : declaredExceptions) {
                    if (exception.isInstance(cause)) {
                        throw cause instanceof Exception ? (Exception) cause : e;
                    }
                }
                throw new CommandExecutionException(
                        "Command execution resulted in a checked exception that was " + "not declared on the gateway",
                        cause);
            }
        }
    }

    private static class NullOnTimeout<R> implements InvocationHandler<R> {

        private final InvocationHandler<R> delegate;

        private NullOnTimeout(InvocationHandler<R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception {
            try {
                return delegate.invoke(proxy, invokedMethod, args);
            } catch (TimeoutException timeout) {
                return null;
            }
        }
    }

    private static class NullOnInterrupted<R> implements InvocationHandler<R> {

        private final InvocationHandler<R> delegate;

        private NullOnInterrupted(InvocationHandler<R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception {
            try {
                return delegate.invoke(proxy, invokedMethod, args);
            } catch (InterruptedException timeout) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private static class WaitForResultWithFixedTimeout<R> implements InvocationHandler<R> {

        private final InvocationHandler<CompletableFuture<R>> delegate;
        private final long timeout;
        private final TimeUnit timeUnit;

        private WaitForResultWithFixedTimeout(InvocationHandler<CompletableFuture<R>> delegate, long timeout,
                                              TimeUnit timeUnit) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception {
            return delegate.invoke(proxy, invokedMethod, args).get(timeout, timeUnit);
        }
    }

    private static class WaitForResultWithTimeoutInArguments<R> implements InvocationHandler<R> {

        private final InvocationHandler<CompletableFuture<R>> delegate;
        private final int timeoutIndex;
        private final int timeUnitIndex;

        private WaitForResultWithTimeoutInArguments(InvocationHandler<CompletableFuture<R>> delegate, int timeoutIndex,
                                                    int timeUnitIndex) {
            this.delegate = delegate;
            this.timeoutIndex = timeoutIndex;
            this.timeUnitIndex = timeUnitIndex;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception {
            return delegate.invoke(proxy, invokedMethod, args)
                           .get(toLong(args[timeoutIndex]), (TimeUnit) args[timeUnitIndex]);
        }

        private long toLong(Object arg) {
            if (int.class.isInstance(arg) || Integer.class.isInstance(arg)) {
                return Long.valueOf((Integer) arg);
            }
            return (Long) arg;
        }
    }

    private static class WaitForResult<R> implements InvocationHandler<R> {

        private final InvocationHandler<CompletableFuture<R>> delegate;

        private WaitForResult(InvocationHandler<CompletableFuture<R>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception {
            return delegate.invoke(proxy, invokedMethod, args).get();
        }
    }

    private static class FireAndForget<R> implements InvocationHandler<R> {

        private final InvocationHandler<CompletableFuture<R>> delegate;

        private FireAndForget(InvocationHandler<CompletableFuture<R>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Exception {
            delegate.invoke(proxy, invokedMethod, args);
            return null;
        }
    }

    private static class MetaDataExtractor {

        private final int argumentIndex;
        private final String metaDataKey;

        private MetaDataExtractor(int argumentIndex, String metaDataKey) {
            this.argumentIndex = argumentIndex;
            this.metaDataKey = metaDataKey;
        }

        @SuppressWarnings("unchecked")
        public void addMetaData(Object[] args, Map<String, Object> metaData) {
            final Object parameterValue = args[argumentIndex];
            if (metaDataKey == null) {
                if (parameterValue != null) {
                    metaData.putAll((Map<? extends String, ?>) parameterValue);
                }
            } else {
                metaData.put(metaDataKey, parameterValue);
            }
        }
    }

    private static class TypeSafeCallbackWrapper<C, R> implements CommandCallback<C, R> {

        private final CommandCallback<C, R> delegate;
        private final ResponseType<R> parameterType;

        @SuppressWarnings("unchecked")
        public TypeSafeCallbackWrapper(CommandCallback<C, R> delegate, ResponseType<R> responseType) {
            this.delegate = delegate;
            parameterType = responseType;
        }

        @Override
        public void onResult(CommandMessage<? extends C> commandMessage,
                             CommandResultMessage<? extends R> commandResultMessage) {
            if (commandResultMessage.isExceptional() || parameterType.matches(commandResultMessage.getPayloadType())) {
                delegate.onResult(commandMessage, commandResultMessage);
            }
        }
    }

    /**
     * Builder class to instantiate a {@link CommandGatewayFactory}.
     * <p>
     * The {@link CommandBus} is a <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private CommandBus commandBus;
        private RetryScheduler retryScheduler;
        private List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors;

        /**
         * Sets the {@link CommandBus} on which to dispatch {@link CommandMessage}s.
         *
         * @param commandBus the {@link CommandBus} on which to dispatch {@link CommandMessage}s
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder commandBus(CommandBus commandBus) {
            assertNonNull(commandBus, "CommandBus may not be null");
            this.commandBus = commandBus;
            return this;
        }

        /**
         * Sets the {@link RetryScheduler} which will decide whether to reschedule commands. May be {@code null} to
         * report failures without rescheduling.
         *
         * @param retryScheduler the {@link RetryScheduler} which will decide whether to reschedule commands
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder retryScheduler(RetryScheduler retryScheduler) {
            this.retryScheduler = retryScheduler;
            return this;
        }

        /**
         * Sets the {@link MessageDispatchInterceptor}s which are invoked before dispatching a {@link CommandMessage} on
         * the {@link CommandBus}. Note that the given {@code dispatchInterceptors} are applied only on commands sent
         * through gateways that have been created using this factory.
         *
         * @param dispatchInterceptors an array of {@link MessageDispatchInterceptor}s which are invoked before
         *                             dispatching a {@link CommandMessage} on the {@link CommandBus}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                MessageDispatchInterceptor<? super CommandMessage<?>>... dispatchInterceptors) {
            return dispatchInterceptors(Arrays.asList(dispatchInterceptors));
        }

        /**
         * Sets the {@link MessageDispatchInterceptor}s which are invoked before dispatching a {@link CommandMessage} on
         * the {@link CommandBus}. Note that the given {@code dispatchInterceptors} are applied only on commands sent
         * through gateways that have been created using this factory.
         *
         * @param dispatchInterceptors a {@link List} of {@link MessageDispatchInterceptor}s which are invoked before
         *                             dispatching a {@link CommandMessage} on the {@link CommandBus}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder dispatchInterceptors(
                List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors) {
            this.dispatchInterceptors = dispatchInterceptors;
            return this;
        }

        /**
         * Initializes a {@link CommandGatewayFactory} as specified through this Builder.
         *
         * @return a {@link CommandGatewayFactory} as specified through this Builder
         */
        public CommandGatewayFactory build() {
            return new CommandGatewayFactory(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(commandBus, "The CommandBus is a hard requirement and should be provided");
        }
    }
}
