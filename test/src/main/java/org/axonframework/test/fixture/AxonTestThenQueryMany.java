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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Implementation of the {@link AxonTestThenMessage then-message-phase} for queryMany of the {@link AxonTestFixture}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AxonTestThenQueryMany
        extends AxonTestThenMessage<AxonTestPhase.Then.QueryMany>
        implements AxonTestPhase.Then.QueryMany {

    private final Reporter reporter = new Reporter();
    private final Object lastQuery;
    private final CompletableFuture<List<?>> actualResult;

    /**
     * Constructs an {@code AxonTestThenQueryMany} for the given parameters.
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
     * @param lastQueryResult      The last result of queryMany handling.
     * @param lastQueryException   The exception thrown during the when-phase, potentially {@code null}.
     */
    public AxonTestThenQueryMany(
            @Nonnull AxonConfiguration configuration,
            @Nonnull AxonTestFixture.Customization customization,
            @Nonnull RecordingCommandBus commandBus,
            @Nonnull RecordingEventSink eventSink,
            @Nonnull RecordingQueryGateway queryGateway,
            @Nonnull Object lastQuery,
            @Nonnull CompletableFuture<List<?>> lastQueryResult,
            @Nullable Throwable lastQueryException
    ) {
        super(configuration, customization, commandBus, eventSink, lastQueryException);
        this.lastQuery = lastQuery;
        this.actualResult = lastQueryResult;
    }

    @Override
    public AxonTestPhase.Then.QueryMany success() {
        StringDescription expectedDescription = new StringDescription();
        if (actualException != null) {
            reporter.reportUnexpectedException(actualException, expectedDescription);
        }
        try {
            // Just join to ensure the query completed successfully
            actualResult.join();
        } catch (Exception e) {
            reporter.reportUnexpectedException(e.getCause() != null ? e.getCause() : e, expectedDescription);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.QueryMany resultSatisfies(@Nonnull Consumer<? super List<?>> consumer) {
        success(); // Ensure query completed successfully first
        try {
            List<?> results = actualResult.join();
            consumer.accept(results);
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate queryMany results", e);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.QueryMany results(@Nonnull List<?> expectedResults) {
        success(); // Ensure query completed successfully first
        try {
            List<?> actualResults = actualResult.join();
            if (!expectedResults.equals(actualResults)) {
                throw new AssertionError("Expected results " + expectedResults + " but got " + actualResults);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate queryMany results", e);
        }
        return this;
    }

    @Override
    public AxonTestPhase.Then.QueryMany resultCount(int expectedSize) {
        success(); // Ensure query completed successfully first
        try {
            List<?> actualResults = actualResult.join();
            if (actualResults.size() != expectedSize) {
                throw new AssertionError("Expected " + expectedSize + " results but got " + actualResults.size());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate queryMany result count", e);
        }
        return this;
    }
}