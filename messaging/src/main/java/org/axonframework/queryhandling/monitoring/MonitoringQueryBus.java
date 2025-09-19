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

import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.StreamingQueryMessage;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

// TODO 3595 - Introduce monitoring logic here.
public class MonitoringQueryBus {

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
    private static class MonitorCallbackContextWriter implements UnaryOperator<Context> {

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
    }
}
