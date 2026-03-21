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

package org.axonframework.eventsourcing.snapshot.store;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.apache.logging.log4j.core.test.junit.LoggerContextSource;
import org.apache.logging.log4j.core.test.junit.Named;
import org.apache.logging.log4j.message.Message;
import org.axonframework.conversion.Converter;
import org.axonframework.eventsourcing.eventstore.Position;
import org.axonframework.eventsourcing.snapshot.api.Snapshot;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link StoreBackedSnapshotter}.
 *
 * @author John Hendrikx
 */
@ExtendWith(MockitoExtension.class)
@LoggerContextSource("log4j2-list-appender.xml")
class StoreBackedSnapshotterTest {
    private static final MessageType TYPE = MessageType.fromString("my-entity#0.1");

    @Mock private SnapshotStore store;
    @Mock private Converter converter;
    @Mock private ProcessingContext processingContext;

    private StoreBackedSnapshotter<String, String> snapshotter;

    @BeforeEach
    void beforeEach() {
        this.snapshotter = new StoreBackedSnapshotter<>(store, TYPE, converter, String.class);
    }

    @Test
    void loadingSnapshotShouldWork() {
        Snapshot expectedSnapshot = new Snapshot(Position.START, "0.1", "payload", Instant.now(), Map.of("answer", "42"));

        when(store.load(eq(TYPE.qualifiedName()), eq("1"), any(ProcessingContext.class))).thenReturn(CompletableFuture.completedFuture(expectedSnapshot));

        assertThat(snapshotter.load("1", processingContext).join()).isEqualTo(expectedSnapshot);
    }

    @Test
    void loadingSnapshotRequiringConversionShouldWork() {
        Snapshot rawSnapshot = new Snapshot(Position.START, "0.1", 0xDEADBEEF, Instant.now(), Map.of("answer", "42"));
        Snapshot expectedSnapshot = rawSnapshot.payload("payload");

        when(converter.convert(0xDEADBEEF, String.class)).thenReturn("payload");
        when(store.load(eq(TYPE.qualifiedName()), eq("1"), any(ProcessingContext.class))).thenReturn(CompletableFuture.completedFuture(rawSnapshot));

        assertThat(snapshotter.load("1", processingContext).join()).isEqualTo(expectedSnapshot);
    }

    @Test
    void whenSnapshotMissingLoadShouldReturnNull() {
        assertThat(snapshotter.load("1", processingContext).join()).isNull();
    }

    @Test
    void whenSnapshotHasMismatchingVersionShouldLogMessageAndReturnNull(@Named("TestAppender") ListAppender appender) {
        Snapshot snapshot = new Snapshot(Position.START, "42.0", 0xDEADBEEF, Instant.now(), Map.of("answer", "42"));

        when(store.load(eq(TYPE.qualifiedName()), eq("1"), any(ProcessingContext.class))).thenReturn(CompletableFuture.completedFuture(snapshot));

        appender.clear();

        assertThat(snapshotter.load("1", processingContext).join()).isNull();

        assertThat(appender.getEvents())
            .extracting(LogEvent::getMessage)
            .extracting(Message::getFormattedMessage)
            .contains("Unsupported snapshot version. Snapshot of my-entity#0.1 for identifier 1 had unsupported version: 42.0");
    }

    @Test
    void successfulSnapshotStoreShouldLogNothing(@Named("TestAppender") ListAppender appender) {
        appender.clear();

        snapshotter.store("1", "payload", Position.START, processingContext);

        assertThat(appender.getEvents()).isEmpty();
    }

    @Test
    void failureToStoreSnapshotShouldOnlyLogWarning(@Named("TestAppender") ListAppender appender) {
        when(store.store(eq(TYPE.qualifiedName()), eq("1"), any(Snapshot.class), any(ProcessingContext.class)))
            .thenReturn(CompletableFuture.failedFuture(new IOException("busy")));

        appender.clear();

        snapshotter.store("1", "payload", Position.START, processingContext);

        assertThat(appender.getEvents())
            .extracting(LogEvent::getMessage)
            .extracting(Message::getFormattedMessage)
            .contains("Snapshotting failed for my-entity#0.1 with identifier 1");
    }

}
