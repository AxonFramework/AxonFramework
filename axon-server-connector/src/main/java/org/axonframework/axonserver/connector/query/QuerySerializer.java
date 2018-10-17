/*
 * Copyright (c) 2018. AxonIQ
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

package org.axonframework.axonserver.connector.query;

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
import io.axoniq.axonserver.grpc.MetaDataValue;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.Serializer;

/**
 * Converter between Axon QueryRequest/QueryResponse and AxonServer GRPC messages.
 * @author Marc Gathier
 */
public class QuerySerializer {

    private final Serializer genericSerializer;

    private final Serializer messageSerializer;

    private final AxonServerConfiguration configuration;

    private final GrpcPayloadSerializer payloadSerializer;

    private final GrpcObjectSerializer<Object> responseTypeSerializer;

    private final GrpcMetadataSerializer metadataSerializer;

    public QuerySerializer(Serializer messageSerializer, Serializer genericSerializer,
                           AxonServerConfiguration configuration) {
        this.genericSerializer = genericSerializer;
        this.messageSerializer = messageSerializer;
        this.configuration = configuration;
        this.payloadSerializer  = new GrpcPayloadSerializer(messageSerializer);
        this.responseTypeSerializer = new GrpcObjectSerializer<>(genericSerializer);
        this.metadataSerializer = new GrpcMetadataSerializer(new GrpcMetaDataConverter(this.messageSerializer));
    }

    public <Q, R> QueryMessage<Q, R> deserializeRequest(QueryRequest query) {
        return new GrpcBackedQueryMessage<>(query, messageSerializer, genericSerializer);
    }

    public QueryResponse serializeResponse(QueryResponseMessage<?> response, String requestMessageId) {
        QueryResponse.Builder builder = QueryResponse.newBuilder();
        if (response.isExceptional()) {
            Throwable exceptionResult = response.exceptionResult();
            builder.setErrorCode(ErrorCode.QUERY_EXECUTION_ERROR.errorCode());
            builder.setErrorMessage(ExceptionSerializer.serialize(configuration.getClientId(), exceptionResult));
        } else {
            builder.setPayload(payloadSerializer.apply(response));
        }
        return builder
                .putAllMetaData(metadataSerializer.apply(response.getMetaData()))
                .setMessageIdentifier(response.getIdentifier())
                .setRequestIdentifier(requestMessageId)
                .build();
    }

    public <Q, R> QueryRequest serializeRequest(QueryMessage<Q, R> queryMessage, int nrResults, long timeout, int priority) {
        return QueryRequest.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setMessageIdentifier(queryMessage.getIdentifier())
                .setQuery(queryMessage.getQueryName())
                .setClientId(configuration.getClientId())
                .setComponentName(configuration.getComponentName())
                .setResponseType(responseTypeSerializer.apply(queryMessage.getResponseType()))
                .setPayload(payloadSerializer.apply(queryMessage))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                        .setKey(ProcessingKey.NR_OF_RESULTS)
                        .setValue(MetaDataValue.newBuilder().setNumberValue(nrResults)))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                        .setKey(ProcessingKey.TIMEOUT)
                        .setValue(MetaDataValue.newBuilder().setNumberValue(timeout)))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                        .setKey(ProcessingKey.PRIORITY)
                        .setValue(MetaDataValue.newBuilder().setNumberValue(priority)))

                .putAllMetaData(metadataSerializer.apply(queryMessage.getMetaData()))
                .build();

    }

    public <R> QueryResponseMessage<R> deserializeResponse(QueryResponse commandResponse) {
        return new GrpcBackedResponseMessage<>(commandResponse, messageSerializer);
    }
}
