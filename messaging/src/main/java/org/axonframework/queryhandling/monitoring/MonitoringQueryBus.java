/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.queryhandling.monitoring;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitorUtils;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryHandlingComponent;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryResponseMessages;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * A {@link QueryBus} wrapper that supports a {@link org.axonframework.monitoring.MessageMonitor}. Actual dispatching
 * and handling of queries is done by a delegate.
 * <p>
 * This {@link MonitoringQueryBus} is typically registered as a
 * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and
 * automatically kicks in whenever a {@link QueryMessage} specific {@link MessageMonitor} is present.
 */
public class MonitoringQueryBus implements QueryBus {

    /**
     * The order in which the {@link MonitoringQueryBus} is applied as a
     * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the
     * {@link QueryBus}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code MonitoringQueryBus} itself. Using the same value can either lead to application of the decorator to
     * the delegate or the {@code MonitoringQueryBus}, depending on the order of registration.
     * <p>
     * The order of the {@code MonitoringQueryBus} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final QueryBus delegate;
    private final MessageMonitor<? super QueryMessage> messageMonitor;

    /**
     * Constructs a {@code MonitoringQueryBus}, decorating the given {@code delegate}.
     *
     * @param delegate     The delegate {@code EventSink} that will handle all publishing.
     * @param messageMonitor the {@link MessageMonitor} to use.
     */
    public MonitoringQueryBus(@Nonnull final QueryBus delegate,
                              @Nullable final MessageMonitor<? super QueryMessage> messageMonitor) {
        this.delegate = requireNonNull(delegate, "delegate cannot be null");
        this.messageMonitor = messageMonitor != null ? messageMonitor : NoOpMessageMonitor.INSTANCE;
    }

    @Override
    public @Nonnull MessageStream<QueryResponseMessage> query(@Nonnull QueryMessage query,
                                                              @Nullable ProcessingContext context) {
        MessageMonitorUtils.registerMonitorCallback(context, messageMonitor, query);

        // TODO: JG?: if we want to report the result messages as well, we need to copy this stream somehow. It would not be a good idea imho to grab this from inside the concrete queryBus impl.
        // TODO: JG?: do we have a MessageStream/copy?
        return delegate.query(query, context);
    }

    @Override
    public @Nonnull SubscriptionQueryResponseMessages subscriptionQuery(@Nonnull SubscriptionQueryMessage query,
                                                                        @Nullable ProcessingContext context,
                                                                        int updateBufferSize) {
        // TODO: JG?: include in #3595?
        return delegate.subscriptionQuery(query, context, updateBufferSize);
    }

    @Override
    public @Nonnull UpdateHandler subscribeToUpdates(@Nonnull SubscriptionQueryMessage query, int updateBufferSize) {
        // TODO: JG?: include in #3595?
        return delegate.subscribeToUpdates(query, updateBufferSize);
    }

    @Override
    public @Nonnull CompletableFuture<Void> emitUpdate(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                       @Nonnull Supplier<SubscriptionQueryUpdateMessage> updateSupplier,
                                                       @Nullable ProcessingContext context) {
        // TODO: JG?: include in #3595?
        return delegate.emitUpdate(filter, updateSupplier, context);
    }

    @Override
    public @Nonnull CompletableFuture<Void> completeSubscriptions(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                                                                  @Nullable ProcessingContext context) {
        // TODO: JG?: include in #3595?
        return delegate.completeSubscriptions(filter, context);
    }

    @Override
    public @Nonnull CompletableFuture<Void> completeSubscriptionsExceptionally(
            @Nonnull Predicate<SubscriptionQueryMessage> filter,
            @Nonnull Throwable cause,
            @Nullable ProcessingContext context
    ) {
        // TODO: JG?: include in #3595?
        return delegate.completeSubscriptionsExceptionally(filter, cause, context);
    }

    @Override
    public QueryBus subscribe(@Nonnull Set<QueryHandlerName> names, @Nonnull QueryHandler queryHandler) {
        // TODO: JG?: include in #3595?
        delegate.subscribe(names, queryHandler);
        return this;
    }

    @Override
    public QueryBus subscribe(@Nonnull QualifiedName queryName,
                              @Nonnull QualifiedName responseName,
                              @Nonnull QueryHandler queryHandler) {
        // TODO: JG?: include in #3595?
        delegate.subscribe(queryName, responseName, queryHandler);
        return this;
    }

    @Override
    public QueryBus subscribe(@Nonnull QueryHandlerName handlerName, @Nonnull QueryHandler queryHandler) {
        delegate.subscribe(handlerName, queryHandler);
        return this;
    }

    @Override
    public QueryBus subscribe(@Nonnull QueryHandlingComponent handlingComponent) {
        delegate.subscribe(handlingComponent);
        return this;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("messageMonitor", messageMonitor);
    }


// private final MessageMonitor<? super QueryMessage> messageMonitor;

    /*
    @Nonnull
    private CompletableFuture<QueryResponseMessage> doQuery(
            @Nonnull QueryMessage query) {
        Assert.isFalse(Publisher.class.isAssignableFrom(query.responseType().getExpectedResponseType()),
                       () -> "Direct query does not support Flux as a return type.");
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(query);
        List<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> handlers =
                getHandlersForMessage(query);
        CompletableFuture<QueryResponseMessage> result = new CompletableFuture<>();
        try {
            ResponseType<?> responseType = query.responseType();
            if (handlers.isEmpty()) {
                throw noHandlerException(query);
            }
            Iterator<MessageHandler<? super QueryMessage, ? extends QueryResponseMessage>> handlerIterator = handlers.iterator();
            boolean invocationSuccess = false;
            while (!invocationSuccess && handlerIterator.hasNext()) {
                LegacyDefaultUnitOfWork<QueryMessage> uow = LegacyDefaultUnitOfWork.startAndGet(query);
                ResultMessage resultMessage =
                        interceptAndInvoke(uow, handlerIterator.next());
                if (resultMessage.isExceptional()) {
                    if (!(resultMessage.exceptionResult() instanceof NoHandlerForQueryException)) {
                        GenericQueryResponseMessage queryResponseMessage =
                                responseType.convertExceptional(resultMessage.exceptionResult())
                                            .map(exceptionalResult -> new GenericQueryResponseMessage(
                                                    messageTypeResolver.resolveOrThrow(exceptionalResult),
                                                    exceptionalResult
                                            ))
                                            .orElse(new GenericQueryResponseMessage(
                                                    messageTypeResolver.resolveOrThrow(resultMessage.exceptionResult()),
                                                    resultMessage.exceptionResult(),
                                                    responseType.responseMessagePayloadType()
                                            ));


                        result.complete(queryResponseMessage);
                        monitorCallback.reportFailure(resultMessage.exceptionResult());
                        return result;
                    }
                } else {
                    result = (CompletableFuture<QueryResponseMessage>) resultMessage.payload();
                    invocationSuccess = true;
                }
            }
            if (!invocationSuccess) {
                throw noSuitableHandlerException(query);
            }
            monitorCallback.reportSuccess();
        } catch (Exception e) {
            result.completeExceptionally(e);
            monitorCallback.reportFailure(e);
        }
        return result;
    }
     */

    /*
    @Override
    public Publisher<QueryResponseMessage> streamingQuery(StreamingQueryMessage query) {
        AtomicReference<Throwable> lastError = new AtomicReference<>();
        return Mono.just(query)
                   .flatMapMany(interceptedQuery -> Mono
                           .just(interceptedQuery)
                           .flatMapMany(this::getStreamingHandlersForMessage)
                           .switchIfEmpty(Flux.error(noHandlerException(interceptedQuery)))
                           .map(handler -> invokeStreaming(interceptedQuery, handler))
                           .flatMap(new CatchLastError(lastError))
                           .doOnEach(new ErrorIfComplete(lastError, interceptedQuery))
                           .next()
                           .doOnEach(new SuccessReporter())
                           .flatMapMany(m -> (Publisher) m.payload())
                   ).contextWrite(new MonitorCallbackContextWriter(messageMonitor, query));
    }
     */

    /**
     * Reports result of streaming query execution to the
     * {@link org.axonframework.monitoring.MessageMonitor.MonitorCallback} (assuming that a monitor callback is attached
     * to the context).
     * <p>
     * The reason for this static class to exist at all is the ability of instantiating {@link SimpleQueryBus} even
     * without Project Reactor on the classpath.
     * </p>
     * <p>
     * If we had Project Reactor on the classpath, this class would be replaced with a lambda (which would compile into
     * inner class). But, inner classes have a reference to an outer class making a single unit together with it. If an
     * inner or outer class had a method with a parameter that belongs to a library which is not on the classpath,
     * instantiation would fail.
     * </p>
     *
     * @author Milan Savic
     */
    private static class SuccessReporter implements Consumer<Signal<?>> {

        @Override
        public void accept(Signal signal) {
            MessageMonitor.MonitorCallback m = signal.getContextView()
                                                     .get(MessageMonitor.MonitorCallback.class);
            if (signal.isOnNext()) {
                m.reportSuccess();
            } else if (signal.isOnError()) {
                m.reportFailure(signal.getThrowable());
            }
        }
    }

    /**
     * Attaches {@link org.axonframework.monitoring.MessageMonitor.MonitorCallback} to the Project Reactor's
     * {@link Context}.
     * <p>
     * The reason for this static class to exist at all is the ability of instantiating {@link SimpleQueryBus} even
     * without Project Reactor on the classpath.
     * </p>
     * <p>
     * If we had Project Reactor on the classpath, this class would be replaced with a lambda (which would compile into
     * inner class). But, inner classes have a reference to an outer class making a single unit together with it. If an
     * inner or outer class had a method with a parameter that belongs to a library which is not on the classpath,
     * instantiation would fail.
     * </p>
     *
     * @author Milan Savic
     */
    /*private static class MonitorCallbackContextWriter implements UnaryOperator<Context> {

        private final MessageMonitor<? super QueryMessage> messageMonitor;
        private final StreamingQueryMessage query;

        private MonitorCallbackContextWriter(MessageMonitor<? super QueryMessage> messageMonitor,
                                             StreamingQueryMessage query) {
            this.messageMonitor = messageMonitor;
            this.query = query;
        }

        @Override
        public Context apply(Context ctx) {
            return ctx.put(MessageMonitor.MonitorCallback.class,
                           messageMonitor.onMessageIngested(query));
        }
    }*/
}
