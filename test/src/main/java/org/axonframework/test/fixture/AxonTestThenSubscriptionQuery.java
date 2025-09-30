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
import org.axonframework.queryhandling.SubscriptionQueryResponse;
import org.hamcrest.StringDescription;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of the {@link AxonTestThenMessage then-message-phase} for subscriptionQuery of the {@link AxonTestFixture}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AxonTestThenSubscriptionQuery
        extends AxonTestThenMessage<AxonTestPhase.Then.SubscriptionQuery>
        implements AxonTestPhase.Then.SubscriptionQuery {

    private final Reporter reporter = new Reporter();
    private final Object lastQuery;
    private final SubscriptionQueryResponse<?, ?> actualResult;

    /**
     * Constructs an {@code AxonTestThenSubscriptionQuery} for the given parameters.
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
     * @param lastQueryResult      The last result of subscriptionQuery handling.
     * @param lastQueryException   The exception thrown during the when-phase, potentially {@code null}.
     */
    public AxonTestThenSubscriptionQuery(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nonnull RecordingQueryGateway queryGateway,
            @Nonnull Object lastQuery,
            @Nullable SubscriptionQueryResponse<?, ?> lastQueryResult,
            @Nullable Throwable lastQueryException
    ) {
        super(configuration, customization, commandBus, eventSink, lastQueryException);
        this.lastQuery = lastQuery;
        this.actualResult = lastQueryResult;
    }

    @Override
    public AxonTestPhase.Then.SubscriptionQuery success() {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        if (actualResult == null) {
            reporter.reportUnexpectedReturnValue(null, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.SubscriptionQuery responseSatisfies(@Nonnull Consumer<? super SubscriptionQueryResponse<?, ?>> consumer) {
        success(); // Ensure query completed successfully first
        consumer.accept(actualResult);
        return this;
    }

    @Override
    public AxonTestPhase.Then.SubscriptionQuery initialResult(@Nonnull Object expectedInitialResult) {
        success(); // Ensure query completed successfully first
        // Validation will be done by the user using responseSatisfies or initialResultSatisfies
        throw new UnsupportedOperationException("Direct initialResult validation is not yet supported. Please use initialResultSatisfies() with custom assertions.");
    }

    @Override
    public AxonTestPhase.Then.SubscriptionQuery initialResultSatisfies(@Nonnull Consumer<Object> consumer) {
        success(); // Ensure query completed successfully first
        // User can subscribe to the Publisher and perform their own assertions
        throw new UnsupportedOperationException("Direct initialResult validation is not yet supported. Please use responseSatisfies() to access the full SubscriptionQueryResponse.");
    }

    @Override
    public AxonTestPhase.Then.SubscriptionQuery updates(@Nonnull List<?> expectedUpdates) {
        success(); // Ensure query completed successfully first
        // Validation will be done by the user using responseSatisfies or updatesSatisfies
        throw new UnsupportedOperationException("Direct updates validation is not yet supported. Please use updatesSatisfies() with custom assertions.");
    }

    @Override
    public AxonTestPhase.Then.SubscriptionQuery updatesSatisfies(@Nonnull Consumer<? super Publisher<?>> consumer) {
        success(); // Ensure query completed successfully first
        // User needs to use responseSatisfies() to get the full SubscriptionQueryResponse
        // and then access updates() which returns a Flux (requires Reactor dependency in user's test code)
        throw new UnsupportedOperationException("Direct updates validation is not yet supported. Please use responseSatisfies() to access the full SubscriptionQueryResponse and call .updates() on it.");
    }
}