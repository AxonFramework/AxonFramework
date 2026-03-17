/*
 * Copyright (c) 2010-2026. Axon Framework
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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.axonframework.axonserver.connector.ErrorCode;
import org.axonframework.messaging.core.GenericMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.queryhandling.GenericQueryResponseMessage;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static io.axoniq.axonserver.grpc.ProcessingKey.*;
import static io.axoniq.axonserver.grpc.query.QueryResponse.newBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionUtils.createProcessingInstruction;

class QueryConverterTest {

    private final String messageIdentifier = UUID.randomUUID().toString();
    private final byte[] payload = "payload".getBytes();
    private final String clientId = "clientId";
    private final String componentName = "componentName";

    @Test
    void convertsQueryRequestToQueryMessage() {
        var queryRequest = QueryRequest
                .newBuilder()
                .setMessageIdentifier(messageIdentifier)
                .setPayload(SerializedObject.newBuilder()
                                            .setType("QueryType")
                                            .setRevision("1")
                                            .setData(ByteString.copyFrom(payload))
                                            .build())
                .addProcessingInstructions(createProcessingInstruction(PRIORITY, 5))
                .build();

        var queryMessage = QueryConverter.convertQueryRequest(queryRequest);

        assertThat(queryMessage).isNotNull();
        assertThat(queryMessage.identifier()).isEqualTo(messageIdentifier);
        assertThat(queryMessage.type().name()).isEqualTo("QueryType");
        assertThat(queryMessage.payloadAs(byte[].class)).isEqualTo(payload);
        assertThat(queryMessage.priority()).hasValue(5);
    }

    @Test
    void convertsQueryMessageToQueryRequest() {
        var type = new MessageType("QueryType", "1");
        var qm = new GenericQueryMessage(
                new GenericMessage(messageIdentifier,
                                   type,
                                   payload,
                                   Map.of("k", "v")),
                7
        );

        var queryRequest = QueryConverter.convertQueryMessage(qm, clientId, componentName);

        assertThat(queryRequest).isNotNull();
        assertThat(queryRequest.getClientId()).isEqualTo(clientId);
        assertThat(queryRequest.getComponentName()).isEqualTo(componentName);
        assertThat(queryRequest.getMessageIdentifier()).isEqualTo(messageIdentifier);
        assertThat(queryRequest.getQuery()).isEqualTo("QueryType");
        assertThat(queryRequest.getPayload().getType()).isEqualTo("QueryType");
        assertThat(queryRequest.getProcessingInstructionsList())
                .anySatisfy(pi -> assertThat(pi.getKey()).isEqualTo(PRIORITY))
                .anySatisfy(pi -> assertThat(pi.getKey()).isEqualTo(NR_OF_RESULTS))
                .anySatisfy(pi -> assertThat(pi.getKey()).isEqualTo(TIMEOUT))
                .anySatisfy(pi -> assertThat(pi.getKey()).isEqualTo(CLIENT_SUPPORTS_STREAMING));
    }

    @Test
    void convertsQueryResponseToQueryResponseMessage() {
        var response = newBuilder()
                .setMessageIdentifier(messageIdentifier)
                .setPayload(SerializedObject.newBuilder()
                                            .setType("java.lang.String")
                                            .setRevision("1")
                                            .setData(ByteString.copyFrom("ok".getBytes()))
                                            .build()
                )
                .putMetaData("m", MetaDataValue.newBuilder()
                                               .setTextValue("v")
                                               .build()
                )
                .build();

        var responseMessage = QueryConverter.convertQueryResponse(response);

        assertThat(responseMessage).isNotNull();
        assertThat(responseMessage.identifier()).isEqualTo(messageIdentifier);
        assertThat(responseMessage.type().name()).isEqualTo("java.lang.String");
        assertThat(responseMessage.payloadAs(byte[].class)).isEqualTo("ok".getBytes());
        assertThat(responseMessage.metadata()).containsEntry("m", "v");
    }

    @Test
    void convertsQueryResponseMessageToQueryResponse() {
        var type = new MessageType("java.lang.String", "1");
        var message = new GenericMessage(messageIdentifier, type, "ok".getBytes(), Map.of("m", "v"));
        var qrm = new GenericQueryResponseMessage(message);

        var response = QueryConverter.convertQueryResponseMessage("req-1", qrm);

        assertThat(response.getRequestIdentifier()).isEqualTo("req-1");
        assertThat(response.getMessageIdentifier()).isEqualTo(messageIdentifier);
        assertThat(response.getPayload().getType()).isEqualTo("java.lang.String");
        assertThat(response.getMetaDataMap()).containsKey("m");
        assertThat(response.getMetaDataMap().get("m").getTextValue()).isEqualTo("v");
    }

    @Test
    void convertsSubscriptionQueryToSubscriptionQueryMessage() {
        var qr = QueryRequest.newBuilder()
                             .setPayload(SerializedObject.newBuilder()
                                                         .setType("QueryType")
                                                         .setRevision("1")
                                                         .setData(ByteString.copyFrom(payload))
                                                         .build())
                             .build();
        var subscriptionQuery = io.axoniq.axonserver.grpc.query.SubscriptionQuery.newBuilder()
                                                                                 .setSubscriptionIdentifier(
                                                                                         messageIdentifier)
                                                                                 .setQueryRequest(qr)
                                                                                 .build();

        var sqm = QueryConverter.convertSubscriptionQueryMessage(subscriptionQuery);

        assertThat(sqm).isNotNull();
        assertThat(sqm.identifier()).isEqualTo(messageIdentifier);
        assertThat(sqm.type().name()).isEqualTo("QueryType");
    }

    @Test
    void convertsSubscriptionQueryUpdateMessageToQueryUpdate() {
        var type = new MessageType("java.lang.String", "1");
        var updateMessage = new GenericSubscriptionQueryUpdateMessage(
                new GenericMessage(messageIdentifier, type, "ok".getBytes(), Map.of("m", "v"))
        );

        var queryUpdate = QueryConverter.convertQueryUpdate(updateMessage);

        assertThat(queryUpdate.getMessageIdentifier()).isEqualTo(messageIdentifier);
        assertThat(queryUpdate.getPayload().getType()).isEqualTo("java.lang.String");
        assertThat(queryUpdate.getMetaDataMap()).containsKey("m");
        assertThat(queryUpdate.getMetaDataMap().get("m").getTextValue()).isEqualTo("v");
    }

    @Test
    void convertsQueryUpdateToSubscriptionQueryUpdateMessage() {
        var qu = QueryUpdate.newBuilder()
                            .setMessageIdentifier(messageIdentifier)
                            .setPayload(SerializedObject.newBuilder()
                                                        .setType("java.lang.String")
                                                        .setRevision("1")
                                                        .setData(ByteString.copyFrom("ok".getBytes()))
                                                        .build())
                            .putMetaData("m", MetaDataValue.newBuilder().setTextValue("v").build())
                            .build();

        var updateMessage = QueryConverter.convertQueryUpdate(qu);

        assertThat(updateMessage.identifier()).isEqualTo(messageIdentifier);
        assertThat(updateMessage.type().name()).isEqualTo("java.lang.String");
        assertThat(updateMessage.metadata()).containsEntry("m", "v");
        assertThat(updateMessage.payloadAs(byte[].class)).isEqualTo("ok".getBytes());
    }

    @Test
    void convertsClientAndThrowableToErrorQueryUpdate() {
        var throwable = new RuntimeException("boom");
        var qu = QueryConverter.convertQueryUpdate(clientId, ErrorCode.QUERY_EXECUTION_ERROR, throwable);

        assertThat(qu.getClientId()).isEqualTo(clientId);
        assertThat(qu.hasErrorMessage()).isTrue();
        assertThat(qu.getErrorMessage().getMessage()).contains("boom");
        assertThat(qu.getErrorMessage().getErrorCode()).isEqualTo("AXONIQ-5001");
        assertThat(qu.getErrorCode()).isEqualTo("AXONIQ-5001");
    }

    @Test
    void convertQueryMessageThrowsOnNonByteArrayPayload() {
        var type = new MessageType("QueryType", "1");
        var qm = new GenericQueryMessage(
                new GenericMessage(type, "not-bytes", Metadata.emptyInstance())
        );

        assertThatThrownBy(() -> QueryConverter.convertQueryMessage(qm, clientId, componentName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload must be of type byte[]");
    }

    @Test
    void convertQueryResponseThrowsOnErrorMessage() {
        var response = newBuilder()
                .setErrorMessage(ErrorMessage.newBuilder()
                                             .setMessage("err")
                                             .build()
                )
                .build();

        assertThatThrownBy(() -> QueryConverter.convertQueryResponse(response))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contained an error");
    }
}
