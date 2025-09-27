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
import org.axonframework.configuration.AxonConfiguration;
import org.axonframework.messaging.MessageStream;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.PayloadMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.StringDescription;

import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of the {@link AxonTestThenMessage then-message-phase} for
 * {@link org.axonframework.queryhandling.QueryMessage QueryMessages} of the {@link AxonTestFixture}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AxonTestThenQuery
        extends AxonTestThenMessage<AxonTestPhase.Then.Query>
        implements AxonTestPhase.Then.Query {

    private final Reporter reporter = new Reporter();
    private final MessageStream<QueryResponseMessage> actualResult;

    /**
     * Constructs an {@code AxonTestThenQuery} for the given parameters.
     *
     * @param configuration        The configuration which this test fixture phase is based on.
     * @param customization        Collection of customizations made for this test fixture.
     * @param commandBus           The recording {@link org.axonframework.commandhandling.CommandBus}, used to capture
     *                             and validate any commands that have been sent.
     * @param eventSink            The recording {@link org.axonframework.eventhandling.EventSink}, used to capture and
     *                             validate any events that have been sent.
     * @param queryBus             The recording {@link org.axonframework.queryhandling.QueryBus}, used to capture and
     *                             validate any queries that have been sent.
     * @param lastQueryResult      The last result of query handling.
     * @param lastQueryException   The exception thrown during the when-phase, potentially {@code null}.
     */
    public AxonTestThenQuery(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nonnull RecordingQueryBus queryBus,
            @Nonnull MessageStream<QueryResponseMessage> lastQueryResult,
            @Nullable Throwable lastQueryException
    ) {
        super(configuration, customization, commandBus, eventSink, lastQueryException);
        this.actualResult = lastQueryResult;
    }

    @Override
    public AxonTestPhase.Then.Query success() {
        return resultMessageSatisfies(q -> {
        });
    }

    @Override
    public AxonTestPhase.Then.Query resultMessageSatisfies(@Nonnull Consumer<? super QueryResponseMessage> consumer) {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        try {
            if (actualResult != null) {
                var nextEntry = actualResult.next();
                if (nextEntry.isPresent()) {
                    consumer.accept(nextEntry.get().message());
                }
            }
        } catch (AssertionError e) {
            reporter.reportWrongResult(actualResult, "Result message to satisfy custom assertions: " + e.getMessage());
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Query resultMessagePayload(@Nonnull Object expectedPayload) {
        StringDescription expectedDescription = new StringDescription();
        StringDescription actualDescription = new StringDescription();
        PayloadMatcher<QueryResponseMessage> expectedMatcher =
                new PayloadMatcher<>(CoreMatchers.equalTo(expectedPayload));
        expectedMatcher.describeTo(expectedDescription);
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        } else {
            try {
                if (actualResult == null) {
                    reporter.reportWrongResult("No query results", expectedDescription.toString());
                } else {
                    var nextEntry = actualResult.next();
                    if (nextEntry.isEmpty()) {
                        reporter.reportWrongResult("No query results", expectedDescription.toString());
                    } else {
                        Object actualPayload = nextEntry.get().message().payload();
                        if (!verifyPayloadEquality(expectedPayload, actualPayload)) {
                            PayloadMatcher<QueryResponseMessage> actualMatcher =
                                    new PayloadMatcher<>(CoreMatchers.equalTo(actualPayload));
                            actualMatcher.describeTo(actualDescription);
                            reporter.reportWrongResult(actualDescription, expectedDescription);
                        }
                    }
                }
            } catch (Exception e) {
                reporter.reportWrongResult(e.getMessage(), expectedDescription.toString());
            }
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Query resultMessagePayloadSatisfies(@Nonnull Consumer<Object> consumer) {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        try {
            if (actualResult != null) {
                var nextEntry = actualResult.next();
                if (nextEntry.isPresent()) {
                    Object payload = nextEntry.get().message().payload();
                    consumer.accept(payload);
                }
            }
        } catch (AssertionError e) {
            reporter.reportWrongResult("Query result payload assertion failed",
                                       "Result message to satisfy custom assertions: " + e.getMessage());
        } catch (Exception e) {
            reporter.reportWrongResult("Query execution failed", e.getMessage());
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Query exception(@Nonnull Class<? extends Throwable> type) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            try {
                Object resultPayload = null;
                if (actualResult != null) {
                    var nextEntry = actualResult.next();
                    if (nextEntry.isPresent()) {
                        resultPayload = nextEntry.get().message().payload();
                    }
                }
                reporter.reportUnexpectedReturnValue(resultPayload, description);
            } catch (Exception e) {
                reporter.reportUnexpectedReturnValue(e, description);
            }
        }
        return super.exception(type);
    }

    @Override
    public AxonTestPhase.Then.Query exception(@Nonnull Class<? extends Throwable> type, @Nonnull String message) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            try {
                Object resultPayload = null;
                if (actualResult != null) {
                    var nextEntry = actualResult.next();
                    if (nextEntry.isPresent()) {
                        resultPayload = nextEntry.get().message().payload();
                    }
                }
                reporter.reportUnexpectedReturnValue(resultPayload, description);
            } catch (Exception e) {
                reporter.reportUnexpectedReturnValue(e, description);
            }
        }
        return super.exception(type, message);
    }

    @Override
    public AxonTestPhase.Then.Query exceptionSatisfies(@Nonnull Consumer<Throwable> consumer) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            try {
                Object resultPayload = null;
                if (actualResult != null) {
                    var nextEntry = actualResult.next();
                    if (nextEntry.isPresent()) {
                        resultPayload = nextEntry.get().message().payload();
                    }
                }
                reporter.reportUnexpectedReturnValue(resultPayload, description);
            } catch (Exception e) {
                reporter.reportUnexpectedReturnValue(e, description);
            }
        }
        return super.exceptionSatisfies(consumer);
    }
}