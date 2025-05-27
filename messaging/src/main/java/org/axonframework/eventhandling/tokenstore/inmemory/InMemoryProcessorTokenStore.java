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

package org.axonframework.eventhandling.tokenstore.inmemory;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.ProcessorTokenStore;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class InMemoryProcessorTokenStore implements ProcessorTokenStore {

    @Override
    public CompletableFuture<Void> initializeTokenSegments(@Nonnull ProcessingContext processingContext,
                                                           int segmentCount) {
        return null;
    }

    @Override
    public CompletableFuture<Void> initializeTokenSegments(@Nonnull ProcessingContext processingContext,
                                                           int segmentCount, @Nullable TrackingToken initialToken) {
        return null;
    }

    @Override
    public CompletableFuture<Void> storeToken(@Nonnull ProcessingContext processingContext,
                                              @Nullable TrackingToken token, int segmentId) {
        return null;
    }

    @Override
    public CompletableFuture<TrackingToken> fetchToken(@Nonnull ProcessingContext processingContext, int segmentId) {
        return null;
    }

    @Override
    public CompletableFuture<TrackingToken> fetchToken(@Nonnull ProcessingContext processingContext,
                                                       @Nonnull Segment segment) {
        return null;
    }

    @Override
    public CompletableFuture<Void> releaseClaim(@Nonnull ProcessingContext processingContext, int segmentId) {
        return null;
    }

    @Override
    public CompletableFuture<Void> initializeSegment(@Nonnull ProcessingContext processingContext,
                                                     @Nullable TrackingToken token, int segmentId) {
        return null;
    }

    @Override
    public CompletableFuture<Void> deleteToken(@Nonnull ProcessingContext processingContext, int segmentId) {
        return null;
    }

    @Override
    public CompletableFuture<int[]> fetchSegments(@Nonnull ProcessingContext processingContext) {
        return null;
    }

    @Override
    public CompletableFuture<List<Segment>> fetchAvailableSegments(@Nonnull ProcessingContext processingContext) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<String>> retrieveStorageIdentifier(@Nonnull ProcessingContext processingContext) {
        return null;
    }
}
