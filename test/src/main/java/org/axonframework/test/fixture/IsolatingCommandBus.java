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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.test.fixture.TestIsolationEnhancer.TEST_ID_METADATA_KEY;

/**
 * Per-fixture {@link CommandBus} wrapper that adds the test isolation identifier as metadata to every dispatched
 * {@link CommandMessage}. This ensures that commands originating from a specific test fixture carry the test's unique
 * identifier, enabling downstream components to correlate and filter by test scope.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
@Internal
class IsolatingCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final String testId;

    /**
     * Constructs a new {@code IsolatingCommandBus} that stamps every dispatched command with the given
     * {@code testId}.
     *
     * @param delegate The {@link CommandBus} to delegate dispatching to after stamping.
     * @param testId   The unique test identifier to attach as metadata.
     */
    IsolatingCommandBus(CommandBus delegate, String testId) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate CommandBus may not be null");
        this.testId = Objects.requireNonNull(testId, "The testId may not be null");
    }

    @Override
    public CompletableFuture<CommandResultMessage> dispatch(CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        return delegate.dispatch(
                command.andMetadata(Map.of(TEST_ID_METADATA_KEY, testId)),
                processingContext
        );
    }

    @Override
    public CommandBus subscribe(QualifiedName name, CommandHandler commandHandler) {
        return delegate.subscribe(name, commandHandler);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
