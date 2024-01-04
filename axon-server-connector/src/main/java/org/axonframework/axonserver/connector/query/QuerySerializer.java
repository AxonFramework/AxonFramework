/*
 * Copyright (c) 2010-2023. Axon Framework
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

import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;
import org.axonframework.axonserver.connector.util.GrpcMetaDataConverter;
import org.axonframework.axonserver.connector.util.GrpcMetadataSerializer;
import org.axonframework.axonserver.connector.util.GrpcObjectSerializer;
import org.axonframework.axonserver.connector.util.GrpcPayloadSerializer;
import org.axonframework.messaging.responsetypes.ConvertingResponseMessage;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

/**
 * Converter between Axon Framework {@link QueryMessage} and {@link QueryResponseMessage} and Axon Server gRPC {@link
 * io.axoniq.axonserver.grpc.query.Query} and {@link QueryResponse} messages.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public class QuerySerializer {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Serializer messageSerializer;
    private final Serializer serializer;
    private final AxonServerConfiguration configuration;

    private final GrpcObjectSerializer<Object> exceptionDetailsSerializer;
    private final GrpcPayloadSerializer payloadSerializer;
    private final GrpcMetadataSerializer metadataSerializer;
    private final GrpcObjectSerializer<Object> responseTypeSerializer;

    /**
     * Instantiate a serializer used to convert Axon {@link QueryMessage}s and {@link QueryResponseMessage}s into Axon
     * Server gRPC messages and vice versa. The provided {@code messageSerializer} is used for converting a message's
     * payload and metadata, whilst the {@code serializer} is used to convert a {@link QueryMessage}'s {@link
     * org.axonframework.messaging.responsetypes.ResponseType}.
     *
     * @param messageSerializer a {@link Serializer} used to de-/serialize an Axon Server gRPC message into {@link
     *                          QueryMessage}s and {@link QueryResponseMessage}s and vice versa
     * @param serializer        a {@link Serializer} used to create a dedicated converter for a {@link QueryMessage}
     *                          {@link org.axonframework.messaging.responsetypes.ResponseType}
     * @param configuration     an {@link AxonServerConfiguration} used to set the configurable component id and name in
     *                          the messages
     */
    public QuerySerializer(Serializer messageSerializer,
                           Serializer serializer,
                           AxonServerConfiguration configuration) {
        this.messageSerializer = messageSerializer;
        this.serializer = serializer;
        this.configuration = configuration;

        this.payloadSerializer = new GrpcPayloadSerializer(messageSerializer);
        this.exceptionDetailsSerializer = new GrpcObjectSerializer<>(messageSerializer);
        this.metadataSerializer = new GrpcMetadataSerializer(new GrpcMetaDataConverter(this.messageSerializer));
        this.responseTypeSerializer = new GrpcObjectSerializer<>(serializer);
    }

    /**
     * Convert a {@link QueryMessage} into a {@link QueryRequest}. The provided {@code nrResults}, {@code timeout} and
     * {@code priority} are all set on the QueryRequest to respectively define the number of results, after which time
     * the query should be aborted and the priority of the query amont others.
     *
     * @param queryMessage the {@link QueryMessage} to convert into a {@link QueryRequest}
     * @param nrResults    an {@code int} denoting the number of expected results
     * @param timeout      a {@code long} specifying the timeout in milliseconds of the created {@link QueryRequest}
     * @param priority     a {@code int} defining the priority among other {@link QueryRequest}s
     * @param <Q>          a generic specifying the payload type of the given {@code queryMessage}
     * @param <R>          a generic specifying the response type of the given {@code queryMessage}
     * @return a {@link QueryRequest} based on the provided {@code queryMessage}
     */
    public <Q, R> QueryRequest serializeRequest(QueryMessage<Q, R> queryMessage,
                                                int nrResults,
                                                long timeout,
                                                int priority) {
        return serializeRequest(queryMessage, nrResults, timeout, priority, false);
    }

    /**
     * Convert a {@link QueryMessage} into a {@link QueryRequest}. The provided {@code nrResults}, {@code timeout} and
     * {@code priority} are all set on the QueryRequest to respectively define the number of results, after which time
     * the query should be aborted and the priority of the query amont others.
     *
     * @param queryMessage the {@link QueryMessage} to convert into a {@link QueryRequest}
     * @param nrResults    an {@code int} denoting the number of expected results
     * @param timeout      a {@code long} specifying the timeout in milliseconds of the created {@link QueryRequest}
     * @param priority     a {@code int} defining the priority among other {@link QueryRequest}s
     * @param stream       indicates whether results of this query should be streamed or not
     * @param <Q>          a generic specifying the payload type of the given {@code queryMessage}
     * @param <R>          a generic specifying the response type of the given {@code queryMessage}
     * @return a {@link QueryRequest} based on the provided {@code queryMessage}
     */
    public <Q, R> QueryRequest serializeRequest(QueryMessage<Q, R> queryMessage, int nrResults, long timeout,
                                                int priority, boolean stream) {
        return QueryRequest.newBuilder()
                           .setTimestamp(System.currentTimeMillis())
                           .setMessageIdentifier(queryMessage.getIdentifier())
                           .setQuery(queryMessage.getQueryName())
                           .setClientId(configuration.getClientId())
                           .setComponentName(configuration.getComponentName())
                           .setResponseType(responseTypeSerializer.apply(queryMessage.getResponseType()
                                                                                     .forSerialization()))
                           .setPayload(payloadSerializer.apply(queryMessage))
                           .addProcessingInstructions(nrOfResults(nrResults))
                           .addProcessingInstructions(timeout(timeout))
                           .addProcessingInstructions(priority(priority))
                           .addProcessingInstructions(supportsStreaming(stream))
                           .putAllMetaData(metadataSerializer.apply(queryMessage.getMetaData()))
                           .build();
    }

    private ProcessingInstruction nrOfResults(int nrOfResults) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(ProcessingKey.NR_OF_RESULTS)
                                    .setValue(MetaDataValue.newBuilder()
                                                           .setNumberValue(nrOfResults))
                                    .build();
    }

    private ProcessingInstruction timeout(long timeout) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(ProcessingKey.TIMEOUT)
                                    .setValue(MetaDataValue.newBuilder()
                                                           .setNumberValue(timeout))
                                    .build();
    }

    private ProcessingInstruction priority(int priority) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(ProcessingKey.PRIORITY)
                                    .setValue(MetaDataValue.newBuilder()
                                                           .setNumberValue(priority))
                                    .build();
    }

    private ProcessingInstruction supportsStreaming(boolean supportsStreaming) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(ProcessingKey.CLIENT_SUPPORTS_STREAMING)
                                    .setValue(MetaDataValue.newBuilder()
                                                           .setBooleanValue(supportsStreaming))
                                    .build();
    }

    /**
     * Convert a {@link QueryResponseMessage} into a {@link QueryResponse}.
     *
     * @param queryResponse    a {@link QueryResponseMessage} to convert into a {@link QueryResponse}
     * @param requestMessageId a {@link String} specifying the identity of the original request message
     * @return a {@link QueryResponse} based on the provided {@code queryResponse}
     */
    public QueryResponse serializeResponse(QueryResponseMessage<?> queryResponse, String requestMessageId) {
        QueryResponse.Builder responseBuilder = QueryResponse.newBuilder();

        if (queryResponse.isExceptional()) {
            Throwable exceptionResult = queryResponse.exceptionResult();
            responseBuilder.setErrorCode(ErrorCode.getQueryExecutionErrorCode(exceptionResult).errorCode());
            responseBuilder.setErrorMessage(
                    ExceptionSerializer.serialize(configuration.getClientId(), exceptionResult)
            );
            Optional<Object> optionalDetails = queryResponse.exceptionDetails();
            if (optionalDetails.isPresent()) {
                responseBuilder.setPayload(exceptionDetailsSerializer.apply(optionalDetails.get()));
            } else {
                logger.warn("Serializing exception [{}] without details.", exceptionResult.getClass(), exceptionResult);
                logger.info("To share exceptional information with the recipient it is recommended to wrap the exception in a QueryExecutionException with provided details.");
            }
        } else {
            responseBuilder.setPayload(payloadSerializer.apply(queryResponse));
        }

        return responseBuilder.putAllMetaData(metadataSerializer.apply(queryResponse.getMetaData()))
                              .setMessageIdentifier(queryResponse.getIdentifier())
                              .setRequestIdentifier(requestMessageId)
                              .build();
    }

    /**
     * Convert a {@link QueryRequest} into a {@link QueryMessage}.
     *
     * @param queryRequest a {@link QueryRequest} to convert into a {@link QueryMessage}
     * @param <Q>          a generic specifying the payload type of the {@link QueryMessage} to convert to
     * @param <R>          a generic specifying the response type of the {@link QueryMessage} to convert to
     * @return a {@link QueryMessage} based on the provided {@code queryRequest}
     */
    public <Q, R> QueryMessage<Q, R> deserializeRequest(QueryRequest queryRequest) {
        return new GrpcBackedQueryMessage<>(queryRequest, messageSerializer, serializer);
    }

    /**
     * Convert a {@link QueryResponse} into a {@link QueryResponseMessage}.
     *
     * @param queryResponse a {@link QueryResponse} to convert into a {@link QueryResponseMessage}
     * @param <R>           a generic specifying the type of the {@link QueryResponseMessage} to convert to
     * @return a {@link QueryResponseMessage} based on the provided {@code queryResponse}
     */
    public <R> QueryResponseMessage<R> deserializeResponse(QueryResponse queryResponse,
                                                           ResponseType<R> expectedResponseType) {
        return new ConvertingResponseMessage<>(
                expectedResponseType, new GrpcBackedResponseMessage<>(queryResponse, messageSerializer)
        );
    }

    /**
     * Converts a {@link QueryResponse} into a {@link QueryResponseMessage}. It does not assume the type of the
     * payload.
     *
     * @param queryResponse a {@link QueryResponse} to convert into a {@link QueryResponseMessage}
     * @return a {@link QueryResponseMessage} based on the provided {@code queryResponse}
     */
    public QueryResponseMessage<?> deserializeResponse(QueryResponse queryResponse) {
        return new GrpcBackedResponseMessage<>(queryResponse, messageSerializer);
    }
}
