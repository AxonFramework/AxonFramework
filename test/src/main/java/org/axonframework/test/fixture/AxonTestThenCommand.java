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

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.messaging.Message;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.PayloadMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import static org.hamcrest.CoreMatchers.*;

class AxonTestThenCommand
        extends AxonTestThenMessage<AxonTestPhase.Then.Command>
        implements AxonTestPhase.Then.Command {

    private final Reporter reporter = new Reporter();

    private final Message<?> lastCommandResult;
    private final Throwable lastCommandException;

    public AxonTestThenCommand(
            NewConfiguration configuration,
            AxonTestFixture.Customization customization,
            RecordingCommandBus commandBus,
            RecordingEventSink eventSink,
            Message<?> lastCommandResult,
            Throwable lastCommandException
    ) {
        super(configuration, customization, commandBus, eventSink, lastCommandException);
        this.lastCommandException = lastCommandException;
        this.lastCommandResult = lastCommandResult;
    }

    @Override
    public AxonTestPhase.Then.Command success() {
        return resultMessage(anything());
    }

    @Override
    public AxonTestPhase.Then.Command resultMessage(Matcher<? super CommandResultMessage<?>> matcher) {
        if (matcher == null) {
            return resultMessage(nullValue());
        }
        StringDescription expectedDescription = new StringDescription();
        matcher.describeTo(expectedDescription);
        if (lastCommandException != null) {
            reporter.reportUnexpectedException(lastCommandException, expectedDescription);
        } else if (!matcher.matches(lastCommandResult)) {
            reporter.reportWrongResult(lastCommandResult, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Command resultMessagePayload(Object expectedPayload) {
        StringDescription expectedDescription = new StringDescription();
        StringDescription actualDescription = new StringDescription();
        PayloadMatcher<CommandResultMessage<?>> expectedMatcher =
                new PayloadMatcher<>(CoreMatchers.equalTo(expectedPayload));
        expectedMatcher.describeTo(expectedDescription);
        if (lastCommandException != null) {
            reporter.reportUnexpectedException(lastCommandException, expectedDescription);
        } else if (!verifyPayloadEquality(expectedPayload, lastCommandResult.getPayload())) {
            PayloadMatcher<CommandResultMessage<?>> actualMatcher =
                    new PayloadMatcher<>(CoreMatchers.equalTo(lastCommandResult.getPayload()));
            actualMatcher.describeTo(actualDescription);
            reporter.reportWrongResult(actualDescription, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Command resultMessagePayloadMatching(Matcher<?> matcher) {
        if (matcher == null) {
            return resultMessagePayloadMatching(nullValue());
        }
        StringDescription expectedDescription = new StringDescription();
        matcher.describeTo(expectedDescription);
        if (lastCommandException != null) {
            reporter.reportUnexpectedException(lastCommandException, expectedDescription);
        } else if (!matcher.matches(lastCommandResult.getPayload())) {
            reporter.reportWrongResult(lastCommandResult.getPayload(), expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Command exception(Class<? extends Throwable> expectedException) {
        return exception(instanceOf(expectedException));
    }

    @Override
    public AxonTestPhase.Then.Command exception(Matcher<?> matcher) {
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        if (lastCommandException == null) {
            reporter.reportUnexpectedReturnValue(lastCommandResult.getPayload(), description);
        }
        if (!matcher.matches(lastCommandException)) {
            reporter.reportWrongException(lastCommandException, description);
        }
        return this;
    }

}
