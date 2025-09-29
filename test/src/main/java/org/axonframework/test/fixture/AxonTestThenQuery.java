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
import org.hamcrest.StringDescription;

import java.util.concurrent.CompletableFuture;
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
    private final Object lastQuery;
    private final CompletableFuture<?> actualResult;

    /**
     * Constructs an {@code AxonTestThenQuery} for the given parameters.
     *
     * @param configuration        The configuration which this test fixture phase is based on.
     * @param customization        Collection of customizations made for this test fixture.
     * @param commandBus           The recording {@link org.axonframework.commandhandling.CommandBus}, used to capture
     *                             and validate any commands that have been sent.
     * @param eventSink            The recording {@link org.axonframework.eventhandling.EventSink}, used to capture and
     *                             validate any events that have been sent.
     * @param queryGateway         The recording {@link org.axonframework.queryhandling.QueryGateway}, used to capture and
     *                             validate any queries that have been sent.
     * @param lastQuery            The last query that was executed.
     * @param lastQueryResult      The last result of query handling.
     * @param lastQueryException   The exception thrown during the when-phase, potentially {@code null}.
     */
    public AxonTestThenQuery(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nonnull RecordingQueryGateway queryGateway,
            @Nonnull Object lastQuery,
            @Nonnull CompletableFuture<?> lastQueryResult,
            @Nullable Throwable lastQueryException
    ) {
        super(configuration, customization, commandBus, eventSink, lastQueryException);
        this.lastQuery = lastQuery;
        this.actualResult = lastQueryResult;
    }

    @Override
    public AxonTestPhase.Then.Query success() {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        try {
            // Just join to ensure the query completed successfully
            actualResult.join();
        } catch (Exception e) {
            reporter.reportUnexpectedException(e, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.Query resultMessageSatisfies(@Nonnull Consumer<? super org.axonframework.queryhandling.QueryResponseMessage> consumer) {
        // Note: QueryGateway doesn't provide QueryResponseMessage, only the actual result
        // This method would need to be reconsidered for the QueryGateway approach
        throw new UnsupportedOperationException("resultMessageSatisfies is not supported with QueryGateway approach. Use resultMessagePayloadSatisfies instead.");
    }

    @Override
    public AxonTestPhase.Then.Query resultMessagePayload(@Nonnull Object expectedPayload) {
        StringDescription expectedDescription = new StringDescription();
        StringDescription actualDescription = new StringDescription();
        expectedDescription.appendText("Query result payload: ").appendValue(expectedPayload);

        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        } else {
            try {
                Object actualPayload = actualResult.join();
                if (!verifyPayloadEquality(expectedPayload, actualPayload)) {
                    actualDescription.appendText("Query result payload: ").appendValue(actualPayload);
                    reporter.reportWrongResult(actualDescription.toString(), expectedDescription.toString());
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
            Object payload = actualResult.join();
            consumer.accept(payload);
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
                Object resultPayload = actualResult.join();
                reporter.reportUnexpectedReturnValue(resultPayload, description);
            } catch (Exception e) {
                // Check if this is the expected exception type
                if (!type.isInstance(e.getCause() != null ? e.getCause() : e)) {
                    description.appendText("Exception of type: ").appendValue(type);
                    reporter.reportWrongException(e.getCause() != null ? e.getCause() : e, description);
                }
                return this;
            }
        }
        return super.exception(type);
    }

    @Override
    public AxonTestPhase.Then.Query exception(@Nonnull Class<? extends Throwable> type, @Nonnull String message) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            try {
                Object resultPayload = actualResult.join();
                reporter.reportUnexpectedReturnValue(resultPayload, description);
            } catch (Exception e) {
                // Check if this is the expected exception type and message
                Throwable actualException = e.getCause() != null ? e.getCause() : e;
                if (!type.isInstance(actualException) || !message.equals(actualException.getMessage())) {
                    description.appendText("Exception of type: ").appendValue(type).appendText(" with message: ").appendValue(message);
                    reporter.reportWrongException(actualException, description);
                }
                return this;
            }
        }
        return super.exception(type, message);
    }

    @Override
    public AxonTestPhase.Then.Query exceptionSatisfies(@Nonnull Consumer<Throwable> consumer) {
        StringDescription description = new StringDescription();
        if (actualException == null) {
            try {
                Object resultPayload = actualResult.join();
                reporter.reportUnexpectedReturnValue(resultPayload, description);
            } catch (Exception e) {
                // Provide the actual exception to the consumer
                consumer.accept(e.getCause() != null ? e.getCause() : e);
                return this;
            }
        }
        return super.exceptionSatisfies(consumer);
    }
}