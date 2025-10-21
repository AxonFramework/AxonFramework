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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.FlowControl;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.grpc.netty.shaded.io.netty.util.internal.OutOfDirectMemoryError;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ExceptionConverter;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.tracing.QueryBusSpanFactory;
import org.axonframework.util.ClasspathResolver;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.axonframework.axonserver.connector.util.ProcessingInstructionUtils.*;

/**
 * The task that processes a single incoming query message from Axon Server. It decides which query type should be
 * invoked based on the incoming query message. It is aware of flow control, hence it will only send response messages
 * when {@link FlowControl#request(long) requested}. Furthermore, sending can be
 * {@link FlowControl#cancel() cancelled}.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.6.0
 */
class QueryProcessingTask implements Runnable, FlowControl {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int DIRECT_QUERY_NUMBER_OF_RESULTS = 1;

    private final QueryBus localSegment;
    private final QueryRequest queryRequest;
    private final ReplyChannel<QueryResponse> responseHandler;
    // TODO #3488 - Replace for QueryConverter in style of CommandConverter
//    private final QuerySerializer serializer;
    private final String clientId;
    private final AtomicReference<StreamableResponse> streamableResultRef = new AtomicReference<>();
    private final AtomicLong requestedBeforeInit = new AtomicLong();
    private final AtomicBoolean cancelledBeforeInit = new AtomicBoolean();
    private final boolean supportsStreaming;

    private final Supplier<Boolean> reactorOnClassPath;
    private final QueryBusSpanFactory spanFactory;

    /**
     * Instantiates a query processing task.
     *
     * @param localSegment    Local instance of {@link QueryBus} used to actually execute the query within this
     *                        application instance.
     * @param queryRequest    The request received from Axon Server.
     * @param responseHandler The {@link ReplyChannel} used for sending items to the Axon Server.
     * @param clientId        The identifier of the client.
     * @param spanFactory     The {@link QueryBusSpanFactory} implementation to use to provide tracing capabilities.
     */
    QueryProcessingTask(QueryBus localSegment,
                        QueryRequest queryRequest,
                        ReplyChannel<QueryResponse> responseHandler,
                        String clientId,
                        QueryBusSpanFactory spanFactory) {
        this(localSegment,
             queryRequest,
             responseHandler,
             clientId,
             ClasspathResolver::projectReactorOnClasspath,
             spanFactory);
    }

    /**
     * Instantiates a query processing task.
     *
     * @param localSegment       Local instance of {@link QueryBus} used to actually execute the query within this
     *                           application instance.
     * @param queryRequest       The request received from Axon Server.
     * @param responseHandler    The {@link ReplyChannel} used for sending items to the Axon Server.
     * @param clientId           The identifier of the client.
     * @param reactorOnClassPath Indicates whether Project Reactor is on the classpath.
     * @param spanFactory        The {@link QueryBusSpanFactory} implementation to use to provide tracing capabilities.
     */
    QueryProcessingTask(QueryBus localSegment,
                        QueryRequest queryRequest,
                        ReplyChannel<QueryResponse> responseHandler,
                        String clientId,
                        Supplier<Boolean> reactorOnClassPath,
                        QueryBusSpanFactory spanFactory) {
        this.localSegment = localSegment;
        this.queryRequest = queryRequest;
        this.responseHandler = responseHandler;
        this.clientId = clientId;
        this.supportsStreaming = supportsStreaming(queryRequest);
        this.reactorOnClassPath = reactorOnClassPath;
        this.spanFactory = spanFactory;
    }

    @Override
    public void run() {
        try {
            logger.debug("Will process query [{}]", queryRequest.getQuery());
            // TODO #3488 - Use QueryConverter (that is styled after CommandConverter) to convert the QueryRequest to a QueryMessage
            QueryMessage queryMessage = null;
            spanFactory.createQueryProcessingSpan(queryMessage).run(() -> {
                if (numberOfResults(queryRequest.getProcessingInstructionsList()) == DIRECT_QUERY_NUMBER_OF_RESULTS) {
                    if (supportsStreaming && reactorOnClassPath.get()) {
                        streamingQuery(queryMessage);
                    } else {
                        directQuery(queryMessage);
                    }
                }
            });
        } catch (RuntimeException | OutOfDirectMemoryError e) {
            sendError(e);
            logger.warn("Query Processor had an exception when processing query [{}]", queryRequest.getQuery(), e);
        }
    }

    @Override
    public void request(long requested) {
        if (requested <= 0) {
            return;
        }
        if (!requestIfInitialized(requested)) {
            requestedBeforeInit.getAndUpdate(current -> {
                try {
                    return Math.addExact(requested, current);
                } catch (ArithmeticException e) {
                    return Long.MAX_VALUE;
                }
            });
            requestIfInitialized(requestedBeforeInit.get());
        }
    }

    @Override
    public void cancel() {
        StreamableResponse result = streamableResultRef.get();
        if (result != null) {
            result.cancel();
        } else {
            cancelledBeforeInit.set(true);
        }
    }

    /**
     * Returns {@code true} if this task is still waiting for a result, and {@code false} otherwise.
     * <p>
     * Note that this would this return {@code true}, even if the streamable result has not been canceled yet!
     *
     * @return {@code true} if this task is still waiting for a result, and {@code false} otherwise.
     */
    public boolean resultPending() {
        return streamableResultRef.get() == null;
    }

    private void streamingQuery(QueryMessage originalQueryMessage) {
        // noinspection unchecked
        QueryMessage streamingQueryMessage = new GenericQueryMessage(
                originalQueryMessage,
                null,
//              FIXME cannot be null!  (Class<R>) originalQueryMessage.responseType().getExpectedResponseType()
                null
        );
        Publisher<QueryResponseMessage> resultPublisher = localSegment.streamingQuery(streamingQueryMessage, null);
        setResult(streamableFluxResult(resultPublisher));
    }

    private void directQuery(QueryMessage queryMessage) {
        // TODO #3488 - Implement as part of Axon Server Query Bus implementation
//        localSegment.query(queryMessage)
//                    .whenComplete((result, e) -> {
//                        if (e != null) {
//                            sendError(e);
//                        } else {
//                            try {
//                                StreamableResponse streamableResponse;
//                                if (supportsStreaming
//                                        && queryMessage.responseType() instanceof MultipleInstancesResponseType) {
//                                    //noinspection unchecked
//                                    streamableResponse = streamableMultiInstanceResult(
//                                            result,
//                                            (Class<T>) queryMessage.responseType().getExpectedResponseType()
//                                    );
//                                } else {
//                                    streamableResponse = streamableInstanceResult(result);
//                                }
//                                setResult(streamableResponse);
//                            } catch (Throwable t) {
//                                sendError(t);
//                            }
//                        }
//                    });
    }

    private void setResult(StreamableResponse result) {
        streamableResultRef.set(result);
        if (cancelledBeforeInit.get()) {
            cancel();
        } else {
            request(requestedBeforeInit.get());
        }
    }

    private StreamableResponse streamableFluxResult(Publisher<QueryResponseMessage> resultPublisher) {
        return new StreamableFluxResponse(Flux.from(resultPublisher),
                                          responseHandler,
                                          queryRequest.getMessageIdentifier(),
                                          clientId);
    }

    @SuppressWarnings("unused")
    private <R> StreamableMultiInstanceResponse<R> streamableMultiInstanceResult(QueryResponseMessage result,
                                                                                 Class<R> responseType) {
        return new StreamableMultiInstanceResponse<>(result,
                                                     responseType,
                                                     responseHandler,
                                                     queryRequest.getMessageIdentifier());
    }

    @SuppressWarnings("unused")
    private StreamableInstanceResponse streamableInstanceResult(QueryResponseMessage result) {
        return new StreamableInstanceResponse(result, responseHandler, queryRequest.getMessageIdentifier());
    }

    private boolean supportsStreaming(QueryRequest queryRequest) {
        boolean axonServerSupportsStreaming = axonServerSupportsQueryStreaming(queryRequest.getProcessingInstructionsList());
        boolean clientSupportsStreaming = clientSupportsQueryStreaming(queryRequest.getProcessingInstructionsList());
        return axonServerSupportsStreaming && clientSupportsStreaming;
    }

    private boolean requestIfInitialized(long requested) {
        StreamableResponse result = streamableResultRef.get();
        if (result != null) {
            result.request(requested);
            return true;
        }
        return false;
    }

    private void sendError(Throwable t) {
        ErrorMessage ex = ExceptionConverter.convertToErrorMessage(clientId, t);
        QueryResponse response =
                QueryResponse.newBuilder()
                             .setErrorCode(ErrorCode.getQueryExecutionErrorCode(t).errorCode())
                             .setErrorMessage(ex)
                             .setRequestIdentifier(queryRequest.getMessageIdentifier())
                             .build();
        responseHandler.sendLast(response);
    }
}
