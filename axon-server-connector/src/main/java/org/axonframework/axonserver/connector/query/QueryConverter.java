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

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.MetadataConverter;
import org.axonframework.common.annotations.Internal;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;

import java.util.concurrent.TimeUnit;

import static org.axonframework.axonserver.connector.util.ExceptionConverter.convertToAxonException;
import static org.axonframework.axonserver.connector.util.ProcessingInstructionUtils.createProcessingInstruction;

/**
 * Utility class to convert queries during
 * {@link AxonServerQueryBusConnector#query(QueryMessage, ProcessingContext) dispatching} and handling of
 * {@link AxonServerQueryBusConnector#subscribe(org.axonframework.queryhandling.QueryHandlerName) subscribed} query
 * handlers in the {@link AxonServerQueryBusConnector}.
 * <p>
 * This utility class is marked as {@link Internal} as it is specific for the {@link AxonServerQueryBusConnector}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public final class QueryConverter {

    /**
     * Converts a {@link QueryRequest} into a {@link QueryMessage}.
     * <p/>
     * This method processes the given QueryRequest by extracting its payload, metadata,
     * and other relevant fields to construct a QueryMessage that represents the request
     * for querying information.
     *
     * @param queryRequest The {@link QueryRequest} to be converted into a {@link QueryMessage}.
     *                     Must not be null.
     * @return A {@link QueryMessage} representation of the provided {@link QueryRequest}.
     *         The returned object contains the extracted payload, metadata, and expected
     *         response type.
     * @throws NullPointerException if the provided {@link QueryRequest} is null.
     */
    @Nonnull
    public static QueryMessage convertQueryRequest(@Nonnull QueryRequest queryRequest) {
        var payload = queryRequest.getPayload();

        var type = new MessageType(payload.getType(), payload.getRevision());
        var responseType = new MessageType(queryRequest.getResponseType().getType(), queryRequest.getResponseType().getRevision());
        return new GenericQueryMessage(
                new GenericMessage(
                        queryRequest.getMessageIdentifier(),
                        type,
                        payload.getData().toByteArray(),
                        MetadataConverter.convertMetadataValuesToGrpc(queryRequest.getMetaDataMap())
                ),
                responseType
        );
    }

    /**
     * Converts a {@link QueryMessage} into a {@link QueryRequest}.
     * <p/>
     * This method processes the provided {@link QueryMessage} and constructs a corresponding
     * {@link QueryRequest} with all necessary details, such as metadata, payload, and other processing
     * instructions.
     *
     * @param query The {@link QueryMessage} to be converted. Must not be null.
     *              The payload must be of type {@code byte[]}, otherwise an {@link IllegalArgumentException} is thrown.
     * @param clientId The identifier of the client making the query. Must not be null.
     * @param componentName The name of the component handling the query. Must not be null.
     * @param streamingQuery A flag indicating whether the query supports streaming responses.
     * @return a {@link QueryRequest} That represents the provided {@link QueryMessage}.
     *         Contains the extracted metadata, payload, and other specific configurations from the original message.
     * @throws IllegalArgumentException if the payload of the {@link QueryMessage} is not of type {@code byte[]}.
     */
    public static QueryRequest convertQueryMessage(@Nonnull QueryMessage query,
                                                   @Nonnull String clientId,
                                                   @Nonnull String componentName,
                                                   boolean streamingQuery) {
        Object payload = query.payload();
        if (!(payload instanceof byte[] payloadAsBytes)) {
            throw new IllegalArgumentException(
                    "Payload must be of type byte[] for AxonServerConnector, but was: "
                            + query.payloadType().getName()
                            + ", consider using a Converter-based QueryBusConnector"
            );
        }
        QueryRequest.Builder builder = QueryRequest.newBuilder();
        addPriority(builder, query);

        return builder.setTimestamp(System.currentTimeMillis())
                      .setClientId(clientId)
                      .setComponentName(componentName)
                      .setMessageIdentifier(query.identifier())
                      .setQuery(query.type().name())
                      // TODO discuss desire for response version too
                      .setResponseType(SerializedObject.newBuilder()
                                                       .setType(query.responseType().name())
                                                       .setRevision(query.responseType().version())
                                                       .build())
                      .putAllMetaData(MetadataConverter.convertGrpcToMetadataValues(query.metadata()))
                      .setPayload(SerializedObject.newBuilder()
                                                  .setData(ByteString.copyFrom(payloadAsBytes))
                                                  .setType(query.type().name())
                                                  .setRevision(query.type().version())
                                                  .build())
                      // TODO #resultHandlersToHit/cardinality is always 1. Is this required?
                      .addProcessingInstructions(nrOfResults(1))
                      // TODO Defaulted to 1H. Do we need this?
                      .addProcessingInstructions(timeout(TimeUnit.HOURS.toMillis(1)))
                      .addProcessingInstructions(supportsStreaming(streamingQuery))
                      .build();
    }

    /**
     * Converts a {@link QueryResponse} into a {@link QueryResponseMessage}.
     * <p/>
     * This method processes the given {@link QueryResponse}, extracts its payload, metadata,
     * and other necessary components to construct a corresponding {@link QueryResponseMessage}.
     * If the {@link QueryResponse} contains an error, an appropriate exception is included
     * in the resulting {@link QueryResponseMessage}.
     *
     * @param queryResponse The {@link QueryResponse} to be converted. Must not be null.
     *                      The {@link QueryResponse} should contain valid payload and metadata
     *                      details necessary to construct the resulting {@link QueryResponseMessage}.
     * @return A {@link QueryResponseMessage} representation of the provided {@link QueryResponse}.
     *         The returned message includes the processed payload, metadata, and any error information,
     *         if applicable.
     */
// TODO: document error behavior
    public static QueryResponseMessage convertQueryResponse(QueryResponse queryResponse) {
        if (queryResponse.hasErrorMessage()) {
            throw new IllegalArgumentException("Query Response contained an error.");
        }
        SerializedObject responsePayload = queryResponse.getPayload();
        var message = new GenericMessage(
                queryResponse.getMessageIdentifier(),
                new MessageType(responsePayload.getType(), responsePayload.getRevision()),
                responsePayload.getData().toByteArray(),
                MetadataConverter.convertMetadataValuesToGrpc(queryResponse.getMetaDataMap())
        );

        return new GenericQueryResponseMessage(message);
    }

    private static void addPriority(QueryRequest.Builder builder, QueryMessage query) {
        query.priority().ifPresent(priority -> {
            var instruction = createProcessingInstruction(ProcessingKey.PRIORITY,
                                                          MetaDataValue.newBuilder()
                                                                       .setNumberValue(priority));
            builder.addProcessingInstructions(instruction);
        });
    }

    private static ProcessingInstruction.Builder nrOfResults(int nrOfResults) {
        return createProcessingInstruction(ProcessingKey.NR_OF_RESULTS, nrOfResults);
    }

    private static ProcessingInstruction.Builder timeout(long timeout) {
        return createProcessingInstruction(ProcessingKey.TIMEOUT, timeout);
    }

    private static ProcessingInstruction.Builder supportsStreaming(boolean supportsStreaming) {
        return createProcessingInstruction(ProcessingKey.CLIENT_SUPPORTS_STREAMING, supportsStreaming);
    }

    private QueryConverter() {
        // Utility class
    }
}
