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
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.messaging.Message;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.PayloadMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.*;

class AxonTestThenCommand
        extends AxonTestThenMessage<AxonTestPhase.Then.Command>
        implements AxonTestPhase.Then.Command {

    private final Reporter reporter = new Reporter();

    private final Message<?> actualResult;

    public AxonTestThenCommand(
            NewConfiguration configuration,
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
        return resultMessage(c -> {
        });
    }

    @Override
    public AxonTestPhase.Then.Command resultMessage(@NotNull Consumer<? super CommandResultMessage<?>> consumer) {
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
        } else if (!verifyPayloadEquality(expectedPayload, actualResult.getPayload())) {
            PayloadMatcher<CommandResultMessage<?>> actualMatcher =
                    new PayloadMatcher<>(CoreMatchers.equalTo(actualResult.getPayload()));
            actualMatcher.describeTo(actualDescription);
            reporter.reportWrongResult(actualDescription, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Command resultMessagePayload(@NotNull Consumer<Object> consumer) {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        try {
            var payload = actualResult.getPayload();
            consumer.accept(payload);
        } catch (AssertionError e) {
            reporter.reportWrongResult(actualResult.getPayload(),
                                       "Result message to satisfy custom assertions: " + e.getMessage());
        }
        return this;
    }

    public AxonTestPhase.Then.Command exception(@Nonnull Matcher<?> matcher) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.getPayload(), description);
        }
        if (!matcher.matches(actualException)) {
            reporter.reportWrongException(actualException, description);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Command exception(@NotNull Class<? extends Throwable> type) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.getPayload(), description);
        }
        return super.exception(type);
    }

    @Override
    public AxonTestPhase.Then.Command exception(@NotNull Class<? extends Throwable> type, @NotNull String message) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.getPayload(), description);
        }
        return super.exception(type, message);
    }

    @Override
    public AxonTestPhase.Then.Command exception(@NotNull Consumer<Throwable> consumer) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.getPayload(), description);
        }
        return super.exception(consumer);
    }
}
