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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.hamcrest.StringDescription;

import java.util.function.Consumer;

/**
 * Implementation of the {@link AxonTestThenMessage then-message-phase} for
 * {@link EventMessage EventMessages} of the {@link AxonTestFixture}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AxonTestThenEvent
        extends AxonTestThenMessage<AxonTestPhase.Then.Event>
        implements AxonTestPhase.Then.Event {

    /**
     * Constructs an {@code AxonTestThenEvent} for the given parameters.
     *
     * @param configuration   The configuration which this test fixture phase is based on.
     * @param customization   Collection of customizations made for this test fixture.
     * @param commandBus      The recording {@link CommandBus}, used to capture and
     *                        validate any commands that have been sent.
     * @param eventSink       The recording {@link EventSink}, used to capture and
     *                        validate any events that have been sent.
     * @param actualException The exception thrown during the when-phase, potentially {@code null}.
     */
    public AxonTestThenEvent(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nullable Throwable actualException
    ) {
        super(configuration, customization, commandBus, eventSink, actualException);
    }

    @Override
    public AxonTestPhase.Then.Event success() {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Event exception(@Nonnull Class<? extends Throwable> type) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(MessageStream.Empty.class.getSimpleName(), description);
        }
        return super.exception(type);
    }

    @Override
    public AxonTestPhase.Then.Event exception(@Nonnull Class<? extends Throwable> type, @Nonnull String message) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(MessageStream.Empty.class.getSimpleName(), description);
        }
        return super.exception(type, message);
    }

    @Override
    public AxonTestPhase.Then.Event exceptionSatisfies(@Nonnull Consumer<Throwable> consumer) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(MessageStream.Empty.class.getSimpleName(), description);
        }
        return super.exceptionSatisfies(consumer);
    }
}
