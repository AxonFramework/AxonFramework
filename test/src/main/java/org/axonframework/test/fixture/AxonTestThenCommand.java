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

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.Message;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.PayloadMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.StringDescription;

import java.util.function.Consumer;

class AxonTestThenCommand
        extends AxonTestThenMessage<AxonTestPhase.Then.Command>
        implements AxonTestPhase.Then.Command {

    private final Reporter reporter = new Reporter();

    private final Message<?> actualResult;

    public AxonTestThenCommand(
            Configuration configuration,
            AxonTestFixture.Customization customization,
            RecordingCommandBus commandBus,
            RecordingEventSink eventSink,
            Message<?> lastCommandResult,
            Throwable lastCommandException
    ) {
        super(configuration, customization, commandBus, eventSink, lastCommandException);
        this.actualResult = lastCommandResult;
    }

    @Override
    public AxonTestPhase.Then.Command success() {
        return resultMessageSatisfies(c -> {
        });
    }

    @Override
    public AxonTestPhase.Then.Command resultMessageSatisfies(
            @Nonnull Consumer<? super CommandResultMessage<?>> consumer) {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        try {
            consumer.accept((CommandResultMessage<?>) actualResult);
        } catch (AssertionError e) {
            reporter.reportWrongResult(actualResult, "Result message to satisfy custom assertions: " + e.getMessage());
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Command resultMessagePayload(@Nonnull Object expectedPayload) {
        StringDescription expectedDescription = new StringDescription();
        StringDescription actualDescription = new StringDescription();
        PayloadMatcher<CommandResultMessage<?>> expectedMatcher =
                new PayloadMatcher<>(CoreMatchers.equalTo(expectedPayload));
        expectedMatcher.describeTo(expectedDescription);
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        } else if (!verifyPayloadEquality(expectedPayload, actualResult.payload())) {
            PayloadMatcher<CommandResultMessage<?>> actualMatcher =
                    new PayloadMatcher<>(CoreMatchers.equalTo(actualResult.payload()));
            actualMatcher.describeTo(actualDescription);
            reporter.reportWrongResult(actualDescription, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Command resultMessagePayloadSatisfies(@Nonnull Consumer<Object> consumer) {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        try {
            var payload = actualResult.payload();
            consumer.accept(payload);
        } catch (AssertionError e) {
            reporter.reportWrongResult(actualResult.payload(),
                                       "Result message to satisfy custom assertions: " + e.getMessage());
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Command exception(@Nonnull Class<? extends Throwable> type) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.payload(), description);
        }
        return super.exception(type);
    }

    @Override
    public AxonTestPhase.Then.Command exception(@Nonnull Class<? extends Throwable> type, @Nonnull String message) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.payload(), description);
        }
        return super.exception(type, message);
    }

    @Override
    public AxonTestPhase.Then.Command exceptionSatisfies(@Nonnull Consumer<Throwable> consumer) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.payload(), description);
        }
        return super.exceptionSatisfies(consumer);
    }
}
