/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.util.internal.OutOfDirectMemoryError;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.ProcessingInstructionHelper;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.queryhandling.GenericStreamingQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.queryhandling.StreamingQueryMessage;
import org.axonframework.util.ClasspathResolver;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.*;

/**
 * The task that processes a single incoming query message from Axon Server. It decides which query type should be
 * invoked based on the incoming query message. It is aware of flow control - it will send response messages only when
 * requested, and it is also possible to cancel sending.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.6
 */
class QueryProcessingTask implements PrioritizedRunnable, FlowControl {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final int DIRECT_QUERY_NUMBER_OF_RESULTS = 1;
    private final QueryBus localSegment;
    private final long priority;
    private final QueryRequest queryRequest;
    private final ReplyChannel<QueryResponse> responseHandler;
    private final QuerySerializer serializer;
    private final String clientId;
    private final AtomicReference<StreamableResult> streamableResultRef = new AtomicReference<>();
    private final AtomicLong requestedBeforeInit = new AtomicLong();
    private final boolean supportsStreaming;

    private final Supplier<Boolean> reactorOnClassPath;

    /**
     * Instantiates query processing task.
     *
     * @param localSegment    local instance of {@link QueryBus} used to actually execute the query withing this
     *                        application instance
     * @param queryRequest    the request received from Axon Server
     * @param responseHandler the {@link ReplyChannel} used for sending items to the Axon Server
     * @param serializer      the serializer used to serialize items
     * @param clientId        the identifier of the client
     */
    QueryProcessingTask(QueryBus localSegment,
                        QueryRequest queryRequest,
                        ReplyChannel<QueryResponse> responseHandler,
                        QuerySerializer serializer,
                        String clientId) {
        this(localSegment,
             queryRequest,
             responseHandler,
             serializer,
             clientId,
             ClasspathResolver::projectReactorOnClasspath);
    }

    /**
     * Instantiates query processing task.
     *
     * @param localSegment       local instance of {@link QueryBus} used to actually execute the query withing this
     *                           application instance
     * @param queryRequest       the request received from Axon SErver
     * @param responseHandler    the {@link ReplyChannel} used for sending items to the Axon Server
     * @param serializer         the serializer used to serialize items
     * @param clientId           the identifier of the client
     * @param reactorOnClassPath indicates whether Project Reactor is on the classpath
     */
    QueryProcessingTask(QueryBus localSegment,
                        QueryRequest queryRequest,
                        ReplyChannel<QueryResponse> responseHandler,
                        QuerySerializer serializer,
                        String clientId,
                        Supplier<Boolean> reactorOnClassPath) {
        this.localSegment = localSegment;
        this.priority = ProcessingInstructionHelper.priority(queryRequest.getProcessingInstructionsList());
        this.queryRequest = queryRequest;
        this.responseHandler = responseHandler;
        this.serializer = serializer;
        this.clientId = clientId;
        this.supportsStreaming = supportsStreaming(queryRequest);
        this.reactorOnClassPath = reactorOnClassPath;
    }

    @Override
    public long priority() {
        return priority;
    }

    @Override
    public void run() {
        try {
            logger.debug("Will process query [{}]", queryRequest.getQuery());
            QueryMessage<Object, Object> queryMessage = serializer.deserializeRequest(queryRequest);
            if (numberOfResults(queryRequest.getProcessingInstructionsList()) == DIRECT_QUERY_NUMBER_OF_RESULTS) {
                if (supportsStreaming && reactorOnClassPath.get()) {
                    streamingQuery(queryMessage);
                } else {
                    directQuery(queryMessage);
                }
            } else {
                scatterGather(queryMessage);
            }
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
        StreamableResult flowControl = streamableResultRef.get();
        if (flowControl != null) {
            flowControl.cancel();
        }
    }

    private <Q, R> void streamingQuery(QueryMessage<Q, R> originalQueryMessage) {
        // noinspection unchecked
        StreamingQueryMessage<Q, R> streamingQueryMessage = new GenericStreamingQueryMessage<>(
                originalQueryMessage,
                originalQueryMessage.getQueryName(),
                (Class<R>) originalQueryMessage.getResponseType().getExpectedResponseType());
        Publisher<QueryResponseMessage<R>> resultPublisher = localSegment.streamingQuery(streamingQueryMessage);
        setResult(streamableFluxResult(resultPublisher));
    }

    private <Q, R, T> void directQuery(QueryMessage<Q, R> queryMessage) {
        localSegment.query(queryMessage)
                    .whenComplete((result, e) -> {
                        if (e != null) {
                            sendError(e);
                        } else {
                            try {
                                StreamableResult streamableResult;
                                if (supportsStreaming
                                        && queryMessage.getResponseType() instanceof MultipleInstancesResponseType) {
                                    //noinspection unchecked
                                    streamableResult =
                                            streamableMultiInstanceResult((QueryResponseMessage<List<T>>) result,
                                                                          (Class<T>) queryMessage.getResponseType()
                                                                                                 .getExpectedResponseType());
                                } else {
                                    streamableResult = streamableInstanceResult(result);
                                }
                                setResult(streamableResult);
                            } catch (Throwable t) {
                                sendError(t);
                            }
                        }
                    });
    }

    private void setResult(StreamableResult result) {
        streamableResultRef.set(result);
        request(requestedBeforeInit.get());
    }

    private <Q, R> void scatterGather(QueryMessage<Q, R> originalQueryMessage) {
        Stream<QueryResponseMessage<R>> result = localSegment.scatterGather(
                originalQueryMessage,
                ProcessingInstructionHelper.timeout(queryRequest.getProcessingInstructionsList()),
                TimeUnit.MILLISECONDS
        );
        result.forEach(r -> responseHandler.send(
                serializer.serializeResponse(r, queryRequest.getMessageIdentifier())
        ));
        responseHandler.complete();
    }

    private <R> StreamableResult streamableFluxResult(Publisher<QueryResponseMessage<R>> resultPublisher) {
        return new StreamableFluxResult(Flux.from(resultPublisher),
                                        responseHandler,
                                        serializer,
                                        queryRequest.getMessageIdentifier(),
                                        clientId);
    }

    private <R> StreamableMultiInstanceResult<R> streamableMultiInstanceResult(QueryResponseMessage<List<R>> result,
                                                                               Class<R> responseType) {
        return new StreamableMultiInstanceResult<>(result,
                                                   responseType,
                                                   responseHandler,
                                                   serializer,
                                                   queryRequest.getMessageIdentifier());
    }

    private StreamableInstanceResult streamableInstanceResult(QueryResponseMessage<?> result) {
        return new StreamableInstanceResult(result, responseHandler, serializer, queryRequest.getMessageIdentifier());
    }

    private boolean supportsStreaming(QueryRequest queryRequest) {
        boolean axonServerSupportsStreaming = axonServerSupportsQueryStreaming(queryRequest.getProcessingInstructionsList());
        boolean clientSupportsStreaming = clientSupportsQueryStreaming(queryRequest.getProcessingInstructionsList());
        return axonServerSupportsStreaming && clientSupportsStreaming;
    }

    private boolean requestIfInitialized(long requested) {
        StreamableResult flowControl = streamableResultRef.get();
        if (flowControl != null) {
            flowControl.request(requested);
            return true;
        }
        return false;
    }

    private void sendError(Throwable t) {
        ErrorMessage ex = ExceptionSerializer.serialize(clientId, t);
        QueryResponse response =
                QueryResponse.newBuilder()
                             .setErrorCode(ErrorCode.getQueryExecutionErrorCode(t).errorCode())
                             .setErrorMessage(ex)
                             .setRequestIdentifier(queryRequest.getMessageIdentifier())
                             .build();
        responseHandler.sendLast(response);
    }
}
