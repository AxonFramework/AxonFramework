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
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.SerializationException;
import org.axonframework.util.ClasspathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.*;
import static org.axonframework.common.StringUtils.nonEmptyOrNull;

/**
 * A {@link Runnable} implementation which is given to a {@link PriorityBlockingQueue} to be consumed by the query
 * {@link ExecutorService}, in order. The {@code priority} is retrieved from the provided {@link QueryRequest} and used
 * to priorities this {@link QueryProcessingTask} among others of it's kind.
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

    private final Supplier<Boolean> reactorOnClassPath;

    QueryProcessingTask(QueryBus localSegment,
                        QueryRequest queryRequest,
                        ReplyChannel<QueryResponse> responseHandler,
                        QuerySerializer serializer, String clientId) {
        this(localSegment,
             queryRequest,
             responseHandler,
             serializer,
             clientId,
             ClasspathResolver::projectReactorOnClasspath);
    }

    QueryProcessingTask(QueryBus localSegment,
                        QueryRequest queryRequest,
                        ReplyChannel<QueryResponse> responseHandler,
                        QuerySerializer serializer, String clientId, Supplier<Boolean> reactorOnClassPath) {
        this.localSegment = localSegment;
        this.priority = ProcessingInstructionHelper.priority(queryRequest.getProcessingInstructionsList());
        this.queryRequest = queryRequest;
        this.responseHandler = responseHandler;
        this.serializer = serializer;
        this.clientId = clientId;
        this.reactorOnClassPath = reactorOnClassPath;
    }

    public long priority() {
        return priority;
    }

    @Override
    public void run() {
        try {
            logger.debug("Will process query [{}]", queryRequest.getQuery());
            QueryMessage<Object, Object> queryMessage = serializer.deserializeRequest(queryRequest);
            if (numberOfResults(queryRequest.getProcessingInstructionsList()) == DIRECT_QUERY_NUMBER_OF_RESULTS) {
                directQuery(queryMessage);
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

    private void directQuery(QueryMessage<Object, Object> originalQueryMessage) {
        ResponseType<Object> adaptedResponseType = determineResponseType(queryRequest.getExpectedResponseType(),
                                                                         originalQueryMessage.getResponseType());
        QueryMessage<Object, Object> alteredQueryMessage = new GenericQueryMessage<>(originalQueryMessage,
                                                                                     originalQueryMessage.getQueryName(),
                                                                                     adaptedResponseType);

        localSegment.query(alteredQueryMessage)
                    .whenComplete((result, e) -> {
                        if (e != null) {
                            sendError(e);
                        } else {
                            try {
                                StreamableResultProvider resultProvider =
                                        streamableResultProviderChain(originalQueryMessage.getResponseType(),
                                                                      adaptedResponseType,
                                                                      result);
                                StreamableResult streamableResult = resultProvider.provide();
                                streamableResultRef.set(streamableResult);
                                request(requestedBeforeInit.get());
                            } catch (Throwable t) {
                                sendError(t);
                            }
                        }
                    });
    }

    private void scatterGather(QueryMessage<Object, Object> originalQueryMessage) {
        Stream<QueryResponseMessage<Object>> result = localSegment.scatterGather(
                originalQueryMessage,
                ProcessingInstructionHelper.timeout(queryRequest.getProcessingInstructionsList()),
                TimeUnit.MILLISECONDS
        );
        result.forEach(r -> responseHandler.send(
                serializer.serializeResponse(r, queryRequest.getMessageIdentifier())
        ));
        responseHandler.complete();
    }

    private StreamableResultProvider streamableResultProviderChain(ResponseType<?> original,
                                                                   ResponseType<?> adapted,
                                                                   QueryResponseMessage<?> result) {
        String requestId = queryRequest.getMessageIdentifier();
        BackwardsCompatibleStreamableResult backwardsCompatible = new BackwardsCompatibleStreamableResult(
                result,
                responseHandler,
                serializer,
                requestId);
        FluxBlockingStreamableResult fluxBlocking = new FluxBlockingStreamableResult(original,
                                                                                     adapted,
                                                                                     result,
                                                                                     responseHandler,
                                                                                     serializer,
                                                                                     requestId,
                                                                                     backwardsCompatible);
        return new StreamingStreamableResultProvider(supportsStreaming(queryRequest),
                                                     adapted,
                                                     result,
                                                     responseHandler,
                                                     serializer,
                                                     clientId,
                                                     requestId,
                                                     fluxBlocking);
    }

    private <R> ResponseType determineResponseType(String expectedResponseType, ResponseType<R> original) {
        if (clientSupportsStreaming(expectedResponseType) && reactorOnClassPath.get()) {
            try {
                Class<?> responseType = Class.forName(expectedResponseType);
                return ResponseTypes.fluxOf(responseType);
            } catch (ClassNotFoundException e) {
                throw new SerializationException(format("Unable to deserialize '%s'.", expectedResponseType), e);
            }
        }
        // backwards compatibility
        return original;
    }

    private boolean supportsStreaming(QueryRequest queryRequest) {
        boolean axonServerSupportsStreaming = axonServerSupportsQueryStreaming(queryRequest.getProcessingInstructionsList());
        boolean clientSupportsStreaming = clientSupportsStreaming(queryRequest.getExpectedResponseType());
        return axonServerSupportsStreaming && clientSupportsStreaming;
    }

    private boolean clientSupportsStreaming(String queryRequest) {
        return nonEmptyOrNull(queryRequest);
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
