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

package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandDispatchInterceptor;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.common.CollectionUtils;
import org.axonframework.common.annotation.MetaData;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;

/**
 * Factory that creates Gateway implementations from custom interface definitions. The behavior of the method is
 * defined by the parameters, declared exceptions and return type of the method.
 * <p/>
 * <em>Supported parameter types:</em><ul>
 * <li>The first parameter of the method is considered the payload of the message. If the first parameter is a Message
 * itself, a new message is created using the payload and metadata of the message passed as parameter.</li>
 * <li>Parameters that are annotated with {@link MetaData @MetaData} are will cause the parameter values to be added as
 * meta data values to the outgoing message.</li>
 * <li>If the last two parameters are of type {@link Long long} and {@link TimeUnit}, they are considered to represent
 * the timeout for the command. The method will block for as long as the command requires to execute, or until the
 * timeout expires.</li>
 * </ul>
 * <p/>
 * <em>Effect of return values</em><ul>
 * <li><code>void</code> return types are always allowed. Unless another parameter makes the method blocking, void
 * methods are non-blocking by default.</li>
 * <li>Declaring a {@link Future} return type will always result in a non-blocking operation. A future is returned
 * that allows you to retrieve the execution's result at your own convenience. Note that declared exceptions and
 * timeouts are ignored.</li>
 * <li>Any other return type will cause the dispatch to block (optionally with timeout) until a result is
 * available</li>
 * </ul>
 * <p/>
 * <em>Effect of declared exceptions</em> <ul>
 * <li>Any checked exception declared on the method will cause it to block (optionally with timeout). If the command
 * results in a declared checked exception, that exception is thrown from the method.</li>
 * <li>Declaring a {@link TimeoutException} will throw that exception when a configured timeout expires. If no such
 * exception is declared, but a timeout is configured, the method will return <code>null</code>.</li>
 * <li>Declaring an {@link InterruptedException} will throw that exception when a thread blocked while waiting for a
 * response is interrupted. Not declaring the exception will have the method return <code>null</code> when a blocked
 * thread is interrupted. Note that when no InterruptedException is declared, the interrupt flag is set back on the
 * interrupted thread</li>
 * </ul>
 * <p/>
 * Finally, the {@link Timeout @Timeout} annotation can be used to define a timeout on a method. This will always cause
 * a method invocation to block until a response is available, or the timeout expires.
 * <p/>
 * Any method will be blocking if: <ul>
 * <li>It declares a return type other than <code>void</code> or <code>Future</code>, or</li>
 * <li>It declares an exception, or</li>
 * <li>The last two parameters are of type {@link TimeUnit} and {@link Long long}, or</li>
 * <li>The method is annotated with {@link Timeout @Timeout}</li>
 * </ul>
 * In other cases, the method is non-blocking and will return immediately after dispatching a command.
 * <p/>
 * This factory is thread safe, and so are the gateways it creates.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class GatewayProxyFactory {

    private final CommandBus commandBus;
    private final RetryScheduler retryScheduler;
    private final List<CommandDispatchInterceptor> dispatchInterceptors;

    /**
     * Initialize the factory sending Commands to the given <code>commandBus</code>, optionally intercepting them with
     * given <code>dispatchInterceptors</code>.
     * <p/>
     * Note that the given <code>dispatchInterceptors</code> are applied only on commands sent through gateways that
     * have been created using this factory.
     *
     * @param commandBus           The CommandBus on which to dispatch the Command Messages
     * @param dispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    public GatewayProxyFactory(CommandBus commandBus, CommandDispatchInterceptor... dispatchInterceptors) {
        this(commandBus, null, dispatchInterceptors);
    }

    /**
     * Initialize the factory sending Commands to the given <code>commandBus</code>, optionally intercepting them with
     * given <code>dispatchInterceptors</code>. The given <code>retryScheduler</code> will reschedule commands for
     * dispatching if a previous attempt resulted in an exception.
     * <p/>
     * Note that the given <code>dispatchInterceptors</code> are applied only on commands sent through gateways that
     * have been created using this factory.
     *
     * @param commandBus                  The CommandBus on which to dispatch the Command Messages
     * @param retryScheduler              The scheduler that will decide whether to reschedule commands, may be
     *                                    <code>null</code> to report failures without rescheduling
     * @param commandDispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    public GatewayProxyFactory(CommandBus commandBus, RetryScheduler retryScheduler,
                               CommandDispatchInterceptor... commandDispatchInterceptors) {
        this(commandBus, retryScheduler, asList(commandDispatchInterceptors));
    }

    /**
     * Initialize the factory sending Commands to the given <code>commandBus</code>, optionally intercepting them with
     * given <code>dispatchInterceptors</code>. The given <code>retryScheduler</code> will reschedule commands for
     * dispatching if a previous attempt resulted in an exception.
     * <p/>
     * Note that the given <code>dispatchInterceptors</code> are applied only on commands sent through gateways that
     * have been created using this factory.
     *
     * @param commandBus                  The CommandBus on which to dispatch the Command Messages
     * @param retryScheduler              The scheduler that will decide whether to reschedule commands, may be
     *                                    <code>null</code> to report failures without rescheduling
     * @param commandDispatchInterceptors The interceptors to invoke before dispatching commands to the Command Bus
     */
    public GatewayProxyFactory(CommandBus commandBus, RetryScheduler retryScheduler,
                               List<CommandDispatchInterceptor> commandDispatchInterceptors) {
        this.retryScheduler = retryScheduler;
        this.commandBus = commandBus;
        this.dispatchInterceptors = new ArrayList<CommandDispatchInterceptor>(commandDispatchInterceptors);
    }

    /**
     * Creates a gateway instance for the given <code>gatewayInterface</code>. The returned instance is a Proxy that
     * implements that interface.
     *
     * @param gatewayInterface The interface declaring the gateway methods
     * @param <T>              The interface declaring the gateway methods
     * @return A Proxy implementation implementing the given interface
     */
    @SuppressWarnings("unchecked")
    public <T> T createGateway(Class<T> gatewayInterface) {
        Map<Method, InvocationHandler> dispatchers = new HashMap<Method, InvocationHandler>();
        for (Method gatewayMethod : gatewayInterface.getMethods()) {
            MetaDataExtractor[] extractors = extractMetaData(gatewayMethod.getParameterAnnotations());

            final Class<?>[] arguments = gatewayMethod.getParameterTypes();
            boolean hasCallbacks = false;
            for (Class<?> argument : arguments) {
                if (CommandCallback.class.isAssignableFrom(argument)) {
                    hasCallbacks = true;
                }
            }
            InvocationHandler dispatcher = new DispatchOnInvocationHandler(commandBus, retryScheduler,
                                                                           dispatchInterceptors, extractors,
                                                                           hasCallbacks);
            if (Future.class.equals(gatewayMethod.getReturnType())) {
                // no wrapping
            } else if (arguments.length >= 3
                    && TimeUnit.class.isAssignableFrom(arguments[arguments.length - 1])
                    && (long.class.isAssignableFrom(arguments[arguments.length - 2])
                    || int.class.isAssignableFrom(arguments[arguments.length - 2]))) {
                dispatcher = wrapToReturnWithTimeoutInArguments(dispatcher, arguments.length - 2, arguments.length - 1);
            } else {
                Timeout timeout = gatewayMethod.getAnnotation(Timeout.class);
                if (timeout == null) {
                    timeout = gatewayMethod.getDeclaringClass().getAnnotation(Timeout.class);
                }
                if (timeout != null) {
                    dispatcher = wrapToReturnWithFixedTimeout(dispatcher, timeout.value(), timeout.unit());
                } else if (!Void.TYPE.equals(gatewayMethod.getReturnType())
                        || gatewayMethod.getExceptionTypes().length > 0) {
                    dispatcher = wrapToWaitForResult(dispatcher);
                } else {
                    dispatcher = wrapToFireAndForget(dispatcher);
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
            dispatchers.put(gatewayMethod, dispatcher);
        }

        return gatewayInterface.cast(
                Proxy.newProxyInstance(gatewayInterface.getClassLoader(),
                                       new Class[]{gatewayInterface},
                                       new GatewayInvocationHandler(dispatchers,
                                                                    commandBus,
                                                                    retryScheduler,
                                                                    dispatchInterceptors)));
    }

    /**
     * Wraps the given <code>delegate</code> in an InvocationHandler that wraps exceptions not declared on the method
     * in a {@link org.axonframework.commandhandling.CommandExecutionException}.
     *
     * @param delegate           The delegate to invoke that potentially throws exceptions
     * @param declaredExceptions The exceptions declared on the method signature
     * @param <R>                The response type of the command handler
     * @return an InvocationHandler that wraps undeclared exceptions in a <code>CommandExecutionException</code>
     */
    protected <R> InvocationHandler<R> wrapUndeclaredExceptions(final InvocationHandler<R> delegate,
                                                                final Class<?>[] declaredExceptions) {
        return new WrapNonDeclaredCheckedExceptions<R>(delegate, declaredExceptions);
    }

    /**
     * Wrap the given <code>delegate</code> in an InvocationHandler that returns null when the
     * <code>delegate</code>
     * throws an InterruptedException.
     *
     * @param delegate The delegate to invoke, potentially throwing an InterruptedException when invoked
     * @param <R>      The response type of the command handler
     * @return an InvocationHandler that wraps returns null when an InterruptedException is thrown
     */
    protected <R> InvocationHandler<R> wrapToReturnNullOnInterrupted(final InvocationHandler<R> delegate) {
        return new NullOnInterrupted<R>(delegate);
    }

    /**
     * Wrap the given <code>delegate</code> in an InvocationHandler that returns null when the
     * <code>delegate</code> throws a TimeoutException.
     *
     * @param delegate The delegate to invoke, potentially throwing a TimeoutException when invoked
     * @param <R>      The response type of the command handler
     * @return an InvocationHandler that wraps returns null when a TimeoutException is thrown
     */
    protected <R> InvocationHandler<R> wrapToReturnNullOnTimeout(final InvocationHandler<R> delegate) {
        return new NullOnTimeout<R>(delegate);
    }

    /**
     * Wrap the given <code>delegate</code> in an InvocationHandler that returns immediately after invoking the
     * <code>delegate</code>.
     *
     * @param delegate The delegate to invoke, potentially throwing an InterruptedException when invoked
     * @param <R>      The response type of the command handler
     * @return an InvocationHandler that wraps returns immediately after invoking the delegate
     */
    protected <R> InvocationHandler<R> wrapToFireAndForget(final InvocationHandler<Future<R>> delegate) {
        return new FireAndForget<R>(delegate);
    }

    /**
     * Wraps the given <code>delegate</code> and waits for the result in the Future to become available. No explicit
     * timeout is provided for the waiting.
     *
     * @param delegate The delegate to invoke, returning a Future
     * @param <R>      The result of the command handler
     * @return the result of the Future, either a return value or an exception
     */
    protected <R> InvocationHandler<R> wrapToWaitForResult(final InvocationHandler<Future<R>> delegate) {
        return new WaitForResult<R>(delegate);
    }

    /**
     * Wraps the given <code>delegate</code> and waits for the result in the Future to become available, with given
     * <code>timeout</code> and <code>timeUnit</code>.
     *
     * @param delegate The delegate to invoke, returning a Future
     * @param timeout  The amount of time to wait for the result to become available
     * @param timeUnit The unit of time to wait
     * @param <R>      The result of the command handler
     * @return the result of the Future, either a return value or an exception
     */
    protected <R> InvocationHandler<R> wrapToReturnWithFixedTimeout(InvocationHandler<Future<R>> delegate,
                                                                    long timeout, TimeUnit timeUnit) {
        return new WaitForResultWithFixedTimeout<R>(delegate, timeout, timeUnit);
    }

    /**
     * Wraps the given <code>delegate</code> and waits for the result in the Future to become available using given
     * indices to resolve the parameters that provide the timeout to use.
     *
     * @param delegate      The delegate to invoke, returning a Future
     * @param timeoutIndex  The index of the argument providing the timeout
     * @param timeUnitIndex The index of the argument providing the time unit
     * @param <R>           The result of the command handler
     * @return the result of the Future, either a return value or an exception
     */
    protected <R> InvocationHandler<R> wrapToReturnWithTimeoutInArguments(
            final InvocationHandler<Future<R>> delegate, int timeoutIndex, int timeUnitIndex) {
        return new WaitForResultWithTimeoutInArguments<R>(delegate, timeoutIndex, timeUnitIndex);
    }

    private boolean contains(Class<?>[] declaredExceptions, Class<?> exceptionClass) {
        for (Class<?> declaredException : declaredExceptions) {
            if (declaredException.isAssignableFrom(exceptionClass)) {
                return true;
            }
        }
        return false;
    }

    private static class GatewayInvocationHandler extends AbstractCommandGateway implements
            java.lang.reflect.InvocationHandler {

        private final Map<Method, InvocationHandler> dispatchers;

        public GatewayInvocationHandler(Map<Method, InvocationHandler> dispatchers, CommandBus commandBus,
                                        RetryScheduler retryScheduler,
                                        List<CommandDispatchInterceptor> dispatchInterceptors) {
            super(commandBus, retryScheduler, dispatchInterceptors);
            this.dispatchers = new HashMap<Method, InvocationHandler>(dispatchers);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
            } else {
                final InvocationHandler invocationHandler = dispatchers.get(method);
                return invocationHandler.invoke(proxy, method, args);
            }
        }
    }

    private MetaDataExtractor[] extractMetaData(Annotation[][] parameterAnnotations) {
        List<MetaDataExtractor> extractors = new ArrayList<MetaDataExtractor>();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            Annotation[] annotations = parameterAnnotations[i];
            final MetaData metaDataAnnotation = CollectionUtils.getAnnotation(annotations, MetaData.class);
            if (metaDataAnnotation != null) {
                extractors.add(new MetaDataExtractor(i, metaDataAnnotation.value()));
            }
        }
        return extractors.toArray(new MetaDataExtractor[extractors.size()]);
    }

    private static class DispatchOnInvocationHandler<R> extends AbstractCommandGateway
            implements InvocationHandler<Future<R>> {

        private final MetaDataExtractor[] metaDataExtractors;
        private final boolean hasCallbacks;

        protected DispatchOnInvocationHandler(CommandBus commandBus, RetryScheduler retryScheduler,
                                              List<CommandDispatchInterceptor> commandDispatchInterceptors,
                                              MetaDataExtractor[] metaDataExtractors, boolean hasCallbacks) {
            super(commandBus, retryScheduler, commandDispatchInterceptors);
            this.metaDataExtractors = metaDataExtractors;
            this.hasCallbacks = hasCallbacks;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Future<R> invoke(Object proxy, Method invokedMethod, Object[] args) {
            Object command = args[0];
            if (metaDataExtractors.length != 0) {
                Map<String, Object> metaDataValues = new HashMap<String, Object>();
                for (MetaDataExtractor extractor : metaDataExtractors) {
                    extractor.addMetaData(args, metaDataValues);
                }
                if (!metaDataValues.isEmpty()) {
                    command = asCommandMessage(command).withMetaData(metaDataValues);
                }
            }
            if (hasCallbacks) {
                List<CommandCallback<R>> callbacks = new LinkedList<CommandCallback<R>>();
                FutureCallback<R> future = new FutureCallback<R>();
                callbacks.add(future);
                for (Object arg : args) {
                    if (arg instanceof CommandCallback) {
                        final CommandCallback<R> callback = (CommandCallback<R>) arg;
                        callbacks.add(callback);
                    }
                }
                send(command, new CompositeCallback(callbacks));
                return future;
            } else {
                return doSend(command);
            }
        }
    }

    private static class CompositeCallback<R> implements CommandCallback<R> {

        private final List<CommandCallback<R>> callbacks;

        @SuppressWarnings("unchecked")
        public CompositeCallback(List<CommandCallback<R>> callbacks) {
            this.callbacks = new ArrayList<CommandCallback<R>>(callbacks);
        }

        @Override
        public void onSuccess(R result) {
            for (CommandCallback<R> callback : callbacks) {
                callback.onSuccess(result);
            }
        }

        @Override
        public void onFailure(Throwable cause) {
            for (CommandCallback callback : callbacks) {
                callback.onFailure(cause);
            }
        }
    }

    private static class WrapNonDeclaredCheckedExceptions<R> implements InvocationHandler<R> {

        private final Class<?>[] declaredExceptions;
        private final InvocationHandler<R> delegate;

        private WrapNonDeclaredCheckedExceptions(InvocationHandler<R> delegate, Class<?>[] declaredExceptions) {
            this.delegate = delegate;
            this.declaredExceptions = declaredExceptions;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable {
            try {
                return delegate.invoke(proxy, invokedMethod, args);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                for (Class<?> exception : declaredExceptions) {
                    if (exception.isInstance(cause)) {
                        throw cause;
                    }
                }
                throw new CommandExecutionException("Command execution resulted in a checked exception that was "
                                                            + "not declared on the gateway", cause);
            }
        }
    }

    private static class NullOnTimeout<R> implements InvocationHandler<R> {

        private final InvocationHandler<R> delegate;

        private NullOnTimeout(InvocationHandler<R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable {
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
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable {
            try {
                return delegate.invoke(proxy, invokedMethod, args);
            } catch (InterruptedException timeout) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private static class WaitForResultWithFixedTimeout<R> implements InvocationHandler<R> {

        private final InvocationHandler<Future<R>> delegate;
        private final long timeout;
        private final TimeUnit timeUnit;

        private WaitForResultWithFixedTimeout(InvocationHandler<Future<R>> delegate, long timeout, TimeUnit timeUnit) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable {
            return delegate.invoke(proxy, invokedMethod, args).get(timeout, timeUnit);
        }
    }

    private static class WaitForResultWithTimeoutInArguments<R> implements InvocationHandler<R> {

        private final InvocationHandler<Future<R>> delegate;
        private final int timeoutIndex;
        private final int timeUnitIndex;

        private WaitForResultWithTimeoutInArguments(InvocationHandler<Future<R>> delegate, int timeoutIndex,
                                                    int timeUnitIndex) {
            this.delegate = delegate;
            this.timeoutIndex = timeoutIndex;
            this.timeUnitIndex = timeUnitIndex;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable {
            return delegate.invoke(proxy, invokedMethod, args).get(toLong(args[timeoutIndex]),
                                                                   (TimeUnit) args[timeUnitIndex]);
        }

        private long toLong(Object arg) {
            if (int.class.isInstance(arg) || Integer.class.isInstance(arg)) {
                return Long.valueOf((Integer) arg);
            }
            return (Long) arg;
        }
    }

    private static class WaitForResult<R> implements InvocationHandler<R> {

        private final InvocationHandler<Future<R>> delegate;

        private WaitForResult(InvocationHandler<Future<R>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable {
            return delegate.invoke(proxy, invokedMethod, args).get();
        }
    }

    private static class FireAndForget<R> implements InvocationHandler<R> {

        private final InvocationHandler<Future<R>> delegate;

        private FireAndForget(InvocationHandler<Future<R>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable {
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

        public void addMetaData(Object[] args, Map<String, Object> metaData) {
            metaData.put(metaDataKey, args[argumentIndex]);
        }
    }

    /**
     * Interface towards the mechanism that handles a method call on a gateway interface method.
     *
     * @param <R> The return type of the method invocation
     */
    public interface InvocationHandler<R> {

        /**
         * Handle the invocation of the given <code>invokedMethod</code>, invoked on given <code>proxy</code> with
         * given
         * <code>args</code>.
         *
         * @param proxy         The proxy on which the method was invoked
         * @param invokedMethod The method being invoked
         * @param args          The arguments of the invocation
         * @return the return value of the invocation
         *
         * @throws Throwable any exceptions that occurred while processing the invocation
         */
        R invoke(Object proxy, Method invokedMethod, Object[] args) throws Throwable;
    }
}
