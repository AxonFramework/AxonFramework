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

package org.axonframework.axonserver.connector.snapshot;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.grpc.event.dcb.AddSnapshotRequest;
import io.axoniq.axonserver.grpc.event.dcb.GetLastSnapshotRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.axonframework.conversion.Converter;
import org.axonframework.eventsourcing.eventstore.AggregateSequenceNumberPosition;
import org.axonframework.eventsourcing.eventstore.GlobalIndexPosition;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.snapshot.api.Snapshot;
import org.axonframework.eventsourcing.snapshot.store.SnapshotStore;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * An Axon Server based implementation of {@link SnapshotStore}.
 * <p>
 * This store stores snapshots in Axon Server and is thread-safe.
 *
 * @author John Hendrikx
 * @since 5.1.0
 */
public class AxonServerSnapshotStore implements SnapshotStore {
    private static final String POSITION_TYPE_KEY = "__AxonFramework__:Position-Type";  // reserved key in metadata
    private static final ByteString NUL = ByteString.copyFrom(new byte[] {0});

    private final AxonServerConnection connection;
    private final Converter converter;

    /**
     * Creates a new instance.
     *
     * @param connection a {@link AxonServerConnection}, cannot be {@code null}
     * @param converter a {@link Converter} for identifiers and payloads, cannot be {@code null}
     */
    public AxonServerSnapshotStore(AxonServerConnection connection, Converter converter) {
        this.connection = Objects.requireNonNull(connection, "The connection parameter must not be null.");
        this.converter = Objects.requireNonNull(converter, "The converter parameter must not be null.");
    }

    private ByteString makeKey(QualifiedName qn, Object identifier) {
        return ByteString.copyFrom(converter.convert(qn.name(), byte[].class))
            .concat(NUL)
            .concat(ByteString.copyFrom(converter.convert(identifier, byte[].class)));
    }

    @Override
    public CompletableFuture<Void> store(QualifiedName qualifiedName, Object identifier, Snapshot snapshot,
                                         @Nullable ProcessingContext context) {
        Objects.requireNonNull(qualifiedName, "The qualifiedName parameter must not be null.");
        Objects.requireNonNull(identifier, "The identifier parameter must not be null.");
        Objects.requireNonNull(snapshot, "The snapshot parameter must not be null.");

        ByteString key = makeKey(qualifiedName, identifier);
        ByteString data = converter.convert(snapshot.payload(), byte[].class) instanceof byte[] ba ? ByteString.copyFrom(ba) : ByteString.EMPTY;

        return connection.snapshotChannel()
            .addSnapshot(AddSnapshotRequest.newBuilder()
                .setKey(key)
                .setSequence(switch(snapshot.position()) {
                    case GlobalIndexPosition gip -> GlobalIndexPosition.toIndex(snapshot.position());
                    case AggregateSequenceNumberPosition asnp -> AggregateSequenceNumberPosition.toSequenceNumber(snapshot.position());
                    default -> throw new IllegalArgumentException("Unsupported position type: " + snapshot.position());
                })
                .setPrune(true)  // this is the standard behavior as documented in the SnapshotStore interface
                .setSnapshot(io.axoniq.axonserver.grpc.event.dcb.Snapshot.newBuilder()
                    .setName(qualifiedName.fullName())
                    .setVersion(snapshot.version())
                    .setTimestamp(snapshot.timestamp().toEpochMilli())  // Axon Server expects milliseconds since epoch
                    .setPayload(data)
                    .putAllMetadata(snapshot.metadata())
                    .putMetadata(POSITION_TYPE_KEY, switch(snapshot.position()) {
                        case GlobalIndexPosition gip -> "GIP";
                        case AggregateSequenceNumberPosition asnp -> "ASNP";
                        default -> throw new IllegalArgumentException("Unsupported position type: " + snapshot.position());
                    })
                )
                .build()
            )
            .thenApply(v -> null);
    }

    @Override
    public CompletableFuture<@Nullable Snapshot> load(QualifiedName qualifiedName, Object identifier,
                                                      @Nullable ProcessingContext context) {
        Objects.requireNonNull(qualifiedName, "The qualifiedName parameter must not be null.");
        Objects.requireNonNull(identifier, "The identifier parameter must not be null.");

        ByteString key = makeKey(qualifiedName, identifier);

        /*
         * Note, even though getLastSnapshot is documented to not return a snapshot when none is available,
         * it actually throws an exception, which is handled in the exceptionally block.
         */

        return connection.snapshotChannel()
            .getLastSnapshot(GetLastSnapshotRequest.newBuilder().setKey(key).build())
            .thenApply(sr -> {
                io.axoniq.axonserver.grpc.event.dcb.Snapshot snapshot = sr.getSnapshot();

                if (snapshot == null) {  // this probably never happens, but just in case the behavior changes
                    return null;
                }

                Map<String, String> metadata = new HashMap<>(snapshot.getMetadataMap());
                String positionType = metadata.remove(POSITION_TYPE_KEY);  // remove this key, so user doesn't notice
                Position position = switch(positionType) {
                    case "GIP" -> new GlobalIndexPosition(sr.getSequence());
                    case "ASNP" -> new AggregateSequenceNumberPosition(sr.getSequence());
                    case null, default -> throw new IllegalArgumentException("Unexpected position type: " + positionType);
                };

                return new Snapshot(
                    position,
                    snapshot.getVersion(),
                    snapshot.getPayload().toByteArray(),
                    Instant.ofEpochMilli(snapshot.getTimestamp()),
                    metadata
                );
            })
            .exceptionally(e -> {
                while (e instanceof CompletionException) {
                    e = e.getCause();
                }

                if (e instanceof StatusRuntimeException sre && (sre.getStatus().getCode() == Status.Code.CANCELLED || sre.getStatus().getCode()  == Status.Code.NOT_FOUND)) {
                    return null;  // Although CANCELLED is a bit odd, this is the current behavior when snapshot is not found.
                }

                throw new CompletionException("Snapshot loading failed for %s with identifier %s".formatted(qualifiedName.toString(), identifier.toString()), e);
            });
    }
}
