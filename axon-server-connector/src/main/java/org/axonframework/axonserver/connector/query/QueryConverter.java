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
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;

import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

import static org.axonframework.axonserver.connector.util.ProcessingInstructionHelper.createProcessingInstruction;

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
public class QueryConverter {

    private QueryConverter() {
        // Utility class
    }

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

    private static void addPriority(QueryRequest.Builder builder, QueryMessage query) {
        // TODO #3488 introduce priority to QueryMessage
        OptionalInt priority = OptionalInt.of(1); //query.priority();
        if (priority.isEmpty()) {
            return;
        }
        var instruction = createProcessingInstruction(ProcessingKey.PRIORITY,
                                                      MetaDataValue.newBuilder().setNumberValue(priority.getAsInt()));
        builder.addProcessingInstructions(instruction).build();
    }

    private static ProcessingInstruction nrOfResults(int nrOfResults) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(ProcessingKey.NR_OF_RESULTS)
                                    .setValue(MetaDataValue.newBuilder()
                                                           .setNumberValue(nrOfResults))
                                    .build();
    }

    private static ProcessingInstruction timeout(long timeout) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(ProcessingKey.TIMEOUT)
                                    .setValue(MetaDataValue.newBuilder().setNumberValue(timeout))
                                    .build();
    }

    private static ProcessingInstruction supportsStreaming(boolean supportsStreaming) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(ProcessingKey.CLIENT_SUPPORTS_STREAMING)
                                    .setValue(MetaDataValue.newBuilder().setBooleanValue(supportsStreaming))
                                    .build();
    }

    public static QueryResponseMessage convertQueryResponse(QueryResponse queryResponse) {
        // TODO implement
        return null;
    }
}
