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
import io.axoniq.axonhub.client.util.MessagePlatformSerializer;
import io.axoniq.axonhub.ProcessingInstruction;
import io.axoniq.axonhub.ProcessingKey;
import io.axoniq.axonhub.QueryRequest;
import io.axoniq.axonhub.QueryResponse;
import io.axoniq.platform.MetaDataValue;
import org.axonframework.queryhandling.QueryMessage;
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

    public QuerySerializer(Serializer serializer) {
        super(serializer);
    }

    public QueryMessage deserializeRequest(QueryRequest query) {
        return new GrpcBackedQueryMessage(query, serializer);
    }

    public QueryResponse serializeResponse(Object response, String messageIdentifier) {
        return QueryResponse.newBuilder()
                            .setPayload(serializePayload(response))
                            .setMessageIdentifier(messageIdentifier)
                            .setSuccess(true)
                            .build();
    }

    public <Q, R> QueryRequest serializeRequest(QueryMessage<Q, R> queryMessage, int nrResults, long timeout, int priority) {
        SerializedObject<byte[]> serializedPayload = MessageSerializer.serializePayload(queryMessage, serializer, byte[].class);

        return QueryRequest.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setMessageIdentifier(UUID.randomUUID().toString())
                .setQuery(queryMessage.getQueryName())
                .setResultName(queryMessage.getResponseType().toString())
                .setPayload(
                        io.axoniq.platform.SerializedObject.newBuilder()
                                .setData(ByteString.copyFrom(serializedPayload.getData()))
                                .setType(serializedPayload.getType().getName())
                                .setRevision(getOrDefault(serializedPayload.getType().getRevision(), ""))
                                .build()
                )
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

    public Object deserializeResponse(QueryResponse commandResponse) {
        return deserializePayload(commandResponse.getPayload());
    }
}
