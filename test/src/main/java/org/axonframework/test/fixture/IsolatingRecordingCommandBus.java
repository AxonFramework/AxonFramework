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

package org.axonframework.test.fixture;

import org.jspecify.annotations.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

/**
 * Per-fixture wrapper around a shared {@link RecordingCommandBus} that filters {@link #recorded()} and
 * {@link #recordedCommands()} results to only include commands carrying the matching test isolation identifier.
 * <p>
 * Dispatching is delegated to the shared recording bus. Only the read (assertion) side is filtered.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
class IsolatingRecordingCommandBus extends RecordingCommandBus {

    private final RecordingCommandBus shared;
    private final String testId;

    /**
     * Constructs a new {@code IsolatingRecordingCommandBus} that filters recordings from the given {@code shared}
     * recording bus by the given {@code testId}.
     *
     * @param shared The shared {@link RecordingCommandBus} that captures all commands.
     * @param testId The unique test identifier to filter by.
     */
    IsolatingRecordingCommandBus(RecordingCommandBus shared, String testId) {
        super(shared);
        this.shared = Objects.requireNonNull(shared, "The shared RecordingCommandBus may not be null");
        this.testId = Objects.requireNonNull(testId, "The testId may not be null");
    }

    @Override
    public CompletableFuture<CommandResultMessage> dispatch(CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        return shared.dispatch(command, processingContext);
    }

    @Override
    public Map<CommandMessage, Message> recorded() {
        var filtered = new LinkedHashMap<CommandMessage, Message>();
        for (var cmd : shared.recordedCommands()) {
            if (testId.equals(cmd.metadata().get(TEST_ID_METADATA_KEY))) {
                filtered.put(cmd, shared.resultOf(cmd));
            }
        }
        return Collections.unmodifiableMap(filtered);
    }

    @Override
    public List<CommandMessage> recordedCommands() {
        return shared.recordedCommands().stream()
                .filter(cmd -> testId.equals(cmd.metadata().get(TEST_ID_METADATA_KEY)))
                .toList();
    }

    @Override
    @Nullable
    public Message resultOf(CommandMessage command) {
        return shared.resultOf(command);
    }

    @Override
    public RecordingCommandBus reset() {
        shared.reset();
        return this;
    }
}
