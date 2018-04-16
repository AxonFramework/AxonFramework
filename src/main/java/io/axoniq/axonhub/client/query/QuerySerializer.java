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

package io.axoniq.axonhub.client.query;

import com.google.protobuf.ByteString;
import io.axoniq.axonhub.ProcessingInstruction;
import io.axoniq.axonhub.ProcessingKey;
import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.QueryResponse;
import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.util.MessagePlatformSerializer;
import io.axoniq.platform.MetaDataValue;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;

import java.util.UUID;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Converter between Axon QueryRequest/QueryResponse and AxonHub GRPC messages.
 * @author Marc Gathier
 */
public class QuerySerializer extends MessagePlatformSerializer {


    private final Serializer genericSerializer;

    public QuerySerializer(Serializer messageSerializer, Serializer genericSerializer,
                           AxonHubConfiguration configuration) {
        super(messageSerializer, configuration);
        this.genericSerializer = genericSerializer;
    }

    public <Q, R> QueryMessage<Q, R> deserializeRequest(QueryRequest query) {
        return new GrpcBackedQueryMessage<>(query, messageSerializer, genericSerializer);
    }

    public QueryResponse serializeResponse(QueryResponseMessage<?> response, String requestMessageId) {
        return QueryResponse.newBuilder()
                            .setPayload(serializePayload(response.getPayload()))
                            .putAllMetaData(serializeMetaData(response.getMetaData()))
                            .setMessageIdentifier(response.getIdentifier())
                            .setRequestIdentifier(requestMessageId)
                            .build();
    }

    public <Q, R> QueryRequest serializeRequest(QueryMessage<Q, R> queryMessage, int nrResults, long timeout, int priority) {
        SerializedObject<byte[]> serializedPayload = MessageSerializer.serializePayload(queryMessage, messageSerializer, byte[].class);
        SerializedObject<byte[]> serializedResponseType = genericSerializer.serialize(queryMessage.getResponseType(), byte[].class);
        return QueryRequest.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setMessageIdentifier(UUID.randomUUID().toString())
                .setQuery(queryMessage.getQueryName())
                .setClientId(configuration.getClientName())
                .setComponentName(configuration.getComponentName())
                .setResponseType(toGrpcSerializedObject(serializedResponseType))
                .setPayload(toGrpcSerializedObject(serializedPayload))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                        .setKey(ProcessingKey.NR_OF_RESULTS)
                        .setValue(MetaDataValue.newBuilder().setNumberValue(nrResults)))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                        .setKey(ProcessingKey.TIMEOUT)
                        .setValue(MetaDataValue.newBuilder().setNumberValue(timeout)))
                .addProcessingInstructions(ProcessingInstruction.newBuilder()
                        .setKey(ProcessingKey.PRIORITY)
                        .setValue(MetaDataValue.newBuilder().setNumberValue(priority)))

                .putAllMetaData(serializeMetaData(queryMessage.getMetaData()))
                .build();

    }

    private io.axoniq.platform.SerializedObject toGrpcSerializedObject(SerializedObject<byte[]> serializedPayload) {
        return io.axoniq.platform.SerializedObject.newBuilder()
                                                  .setData(ByteString.copyFrom(serializedPayload.getData()))
                                                  .setType(serializedPayload.getType().getName())
                                                  .setRevision(getOrDefault(serializedPayload.getType().getRevision(), ""))
                                                  .build();
    }

    public <R> QueryResponseMessage<R> deserializeResponse(QueryResponse commandResponse) {
        return new GrpcBackedResponseMessage<>(commandResponse, messageSerializer);
    }
}
