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
import jakarta.annotation.Nullable;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;
import org.axonframework.test.matchers.PayloadMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.StringDescription;

import java.util.function.Consumer;

/**
 * Implementation of the {@link AxonTestThenMessage then-message-phase} for query handling of the
 * {@link AxonTestFixture}.
 * <p>
 * This class provides assertion methods for verifying query results after dispatching a query in the When phase.
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 */
class AxonTestThenQuery
        extends AxonTestThenMessage<AxonTestPhase.Then.Query>
        implements AxonTestPhase.Then.Query {

    private final Reporter reporter = new Reporter();
    private final Message actualResult;

    /**
     * Constructs an {@code AxonTestThenQuery} for the given parameters.
     *
     * @param configuration      The configuration which this test fixture phase is based on.
     * @param customization      Collection of customizations made for this test fixture.
     * @param commandBus         The recording {@link CommandBus}, used to capture
     *                           and validate any commands that have been sent.
     * @param eventSink          The recording {@link EventSink}, used to capture and
     *                           validate any events that have been sent.
     * @param lastQueryResult    The last result of query handling.
     * @param lastQueryException The exception thrown during the when-phase, potentially {@code null}.
     */
    public AxonTestThenQuery(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nullable Message lastQueryResult,
            @Nullable Throwable lastQueryException
    ) {
        super(configuration, customization, commandBus, eventSink, lastQueryException);
        this.actualResult = lastQueryResult;
    }

    @Override
    public AxonTestPhase.Then.Query expectResult(@Nonnull Object expectedResult) {
        StringDescription expectedDescription = new StringDescription();
        StringDescription actualDescription = new StringDescription();
        PayloadMatcher<QueryResponseMessage> expectedMatcher =
                new PayloadMatcher<>(CoreMatchers.equalTo(expectedResult));
        expectedMatcher.describeTo(expectedDescription);

        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        } else if (actualResult == null) {
            reporter.reportWrongResult((Object) null, "Expected result: " + expectedDescription);
        } else if (!verifyPayloadEquality(expectedResult, actualResult.payload())) {
            PayloadMatcher<QueryResponseMessage> actualMatcher =
                    new PayloadMatcher<>(CoreMatchers.equalTo(actualResult.payload()));
            actualMatcher.describeTo(actualDescription);
            reporter.reportWrongResult(actualDescription, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Query expectResultSatisfies(@Nonnull Consumer<Object> consumer) {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        } else if (actualResult == null) {
            reporter.reportWrongResult((Object) null, "Expected result to satisfy custom assertions");
        }

        try {
            var payload = actualResult.payload();
            consumer.accept(payload);
        } catch (AssertionError e) {
            reporter.reportWrongResult(actualResult.payload(),
                                       "Query result to satisfy custom assertions: " + e.getMessage());
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Query success() {
        return expectResultSatisfies(r -> {
            // Just verify no exception was thrown
        });
    }

    @Override
    public AxonTestPhase.Then.Query exception(@Nonnull Class<? extends Throwable> type) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.payload(), description);
        }
        return super.exception(type);
    }

    @Override
    public AxonTestPhase.Then.Query exception(@Nonnull Class<? extends Throwable> type, @Nonnull String message) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.payload(), description);
        }
        return super.exception(type, message);
    }

    @Override
    public AxonTestPhase.Then.Query exceptionSatisfies(@Nonnull Consumer<Throwable> consumer) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            reporter.reportUnexpectedReturnValue(actualResult == null ? null : actualResult.payload(), description);
        }
        return super.exceptionSatisfies(consumer);
    }
}
