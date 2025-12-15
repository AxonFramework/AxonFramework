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

package org.axonframework.axonserver.connector.event.axon;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.event.DcbEventChannel;
import io.axoniq.axonserver.grpc.event.dcb.ConsistencyCondition;
import io.axoniq.axonserver.grpc.event.dcb.Event;
import io.axoniq.axonserver.grpc.event.dcb.Tag;
import io.axoniq.axonserver.grpc.event.dcb.TaggedEvent;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

/**
 * Integration tests for {@link PersistentStreamMessageSource} using a DCB (Dynamic Consistency Boundary) context.
 * <p>
 * This test class extends {@link AbstractPersistentStreamMessageSourceIT} and provides DCB-specific
 * event publishing using {@link DcbEventChannel}.
 * <p>
 * <b>Note:</b> Aggregate tag-based filtering tests are NOT included in DCB context tests because:
 * <ul>
 *     <li>DCB events use tags which are not accessible on the client side after conversion through persistent streams</li>
 *     <li>The {@link PersistentStreamMessageSource#subscribe(org.axonframework.messaging.eventstreaming.EventCriteria,
 *         java.util.function.BiFunction) subscribe(EventCriteria, ...)} method constructs tags from
 *         {@code LegacyResources.AGGREGATE_TYPE_KEY} and {@code AGGREGATE_IDENTIFIER_KEY} which are only
 *         populated for legacy aggregate-based events</li>
 * </ul>
 * For aggregate tag filtering tests, see {@link LegacyAggregatePersistentStreamMessageSourceIT}.
 *
 * @author Mateusz Nowak
 * @see AbstractPersistentStreamMessageSourceIT
 * @see LegacyAggregatePersistentStreamMessageSourceIT
 */
@Testcontainers
class DcbPersistentStreamMessageSourceIT extends AbstractPersistentStreamMessageSourceIT {

    @SuppressWarnings("resource")
    @Container
    private static final AxonServerContainer axonServerContainer =
            new AxonServerContainer("docker.axoniq.io/axoniq/axonserver:2025.2.0")
                    .withDevMode(true)
                    .withDcbContext(true);

    @BeforeAll
    static void beforeAll() {
        initializeConnection(axonServerContainer, "DcbPersistentStreamMessageSourceIT");
    }

    @AfterAll
    static void afterAll() {
        cleanupConnection(axonServerContainer);
    }

    @Override
    protected AxonServerContainer getContainer() {
        return axonServerContainer;
    }

    @Override
    protected void purgeEvents() throws Exception {
        AxonServerContainerUtils.purgeEventsFromAxonServer(
                axonServerContainer.getHost(),
                axonServerContainer.getHttpPort(),
                CONTEXT,
                AxonServerContainerUtils.DCB_CONTEXT
        );
    }

    @Override
    protected void publishEvent(String aggregateId, String aggregateType, String eventType, long sequenceNumber)
            throws Exception {
        DcbEventChannel dcbChannel = connection.dcbEventChannel();
        DcbEventChannel.AppendEventsTransaction tx = dcbChannel.startTransaction(
                ConsistencyCondition.getDefaultInstance()
        );

        Event event = Event.newBuilder()
                           .setIdentifier(UUID.randomUUID().toString())
                           .setTimestamp(System.currentTimeMillis())
                           .setName(eventType)
                           .setVersion("1.0")
                           .setPayload(ByteString.copyFromUtf8("{}"))
                           .build();

        // Create a tag for the aggregate (key=aggregateType, value=aggregateId)
        // Note: These tags are NOT accessible client-side through persistent streams
        Tag aggregateTag = Tag.newBuilder()
                              .setKey(ByteString.copyFromUtf8(aggregateType))
                              .setValue(ByteString.copyFromUtf8(aggregateId))
                              .build();

        TaggedEvent taggedEvent = TaggedEvent.newBuilder()
                                             .setEvent(event)
                                             .addTag(aggregateTag)
                                             .build();

        tx.append(taggedEvent);
        tx.commit().get();
    }

    // DCB-specific tests can be added here if needed
    // Aggregate tag filtering is NOT supported in DCB context - see class javadoc
}
