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

package org.axonframework.eventhandling.processors.streaming.token.store;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Describes a component capable of storing and retrieving event {@link TrackingToken tracking tokens}.
 * <p>
 * A {@link org.axonframework.eventhandling.processors.streaming.StreamingEventProcessor} that is tracking an event
 * stream can use the store to keep track of its position in the event stream. Tokens are stored by process name and
 * segment index, enabling the same processor to be distributed over multiple processes or machines.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @since 3.0.0
 */
public interface TokenStore {

    /**
     * Initializes a given number of segments for the given {@code processorName} to track its tokens.
     * <p>
     * Returns a {@link CompletableFuture} that completes when the segments were successfully initialized.
     * The returned future will complete exceptionally with an {@link UnableToClaimTokenException} when
     * a segment to be initialized already exists.
     * <p>
     * This method should only be invoked when <b>no</b> tokens have been stored for the given processor, yet.
     * <p>
     * This method will store the given {@code initialToken} for all segments as the starting point for the processor
     * with the given {@code processorName}, but not claim them. It will create the segments ranging from {@code 0}
     * until {@code segmentCount - 1}.
     * <p>
     * The exact behavior when this method is called while tokens were already present is undefined in case the token
     * already present is not owned by the initializing process.
     *
     * @param processorName The name of the processor to initialize segments for.
     * @param segmentCount  The number of segments to initialize.
     * @param initialToken  The initial token which is used as a starting point for the processor.
     * @param context       The processing context to use when initializing the segments, if any.
     * @return A {@code CompletableFuture} that completes when the segments have been initialized
     */
    @Nonnull
    CompletableFuture<List<Segment>> initializeTokenSegments(
            @Nonnull String processorName,
            int segmentCount,
            @Nullable TrackingToken initialToken,
            @Nullable ProcessingContext context
    );

    /**
     * Stores the given {@code token} in the store.
     * <p>
     * Returns a {@link CompletableFuture} that completes when the token was successfully stored.
     * The returned future will complete exceptionally with an {@link UnableToClaimTokenException} if
     * the token was not initialized, or was claimed by another process.
     * <p>
     * The token marks the current position of the process with given {@code processorName} and {@code segment}. The
     * given {@code token} may be {@code null}. Any claims made by the current process have their timestamp updated.
     *
     * @param token         The token to store for a given process and segment. May be {@code null}.
     * @param processorName The name of the process for which to store the token.
     * @param segmentId     The index of the segment for which to store the token.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} that completes when the token has been stored.
     */
    @Nonnull
    CompletableFuture<Void> storeToken(@Nullable TrackingToken token,
                                       @Nonnull String processorName,
                                       int segmentId,
                                       @Nullable ProcessingContext context);

    /**
     * Fetches the last stored {@link TrackingToken token} for the given {@code processorName} and {@code segmentId}.
     * <p>
     * Returns a {@link CompletableFuture} that completes with the fetched token, or {@code null} if the token was {@code null}.
     * The returned future will complete exceptionally with an {@link UnableToClaimTokenException} if
     * the token did not exist or was claimed by another process.
     * <p>
     * The token will be claimed by the current process (JVM instance), preventing access by other instances. To release
     * the claim, use {@link #releaseClaim(String, int, ProcessingContext)}
     *
     * @param processorName The process name for which to fetch the token.
     * @param segmentId     The segment index for which to fetch the token.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} with the last stored TrackingToken or {@code null} if the store holds no
     * token for the given process and segment.
     */
    @Nonnull
    CompletableFuture<TrackingToken> fetchToken(@Nonnull String processorName,
                                                int segmentId,
                                                @Nullable ProcessingContext context);

    /**
     * Fetches the last stored {@link TrackingToken token} for the given {@code processorName} and {@code segment}.
     * <p>
     * Returns a {@link CompletableFuture} that completes with the fetched token, or {@code null} if the token was {@code null}.
     * The returned future will complete exceptionally with an {@link UnableToClaimTokenException} if
     * the token did not exist or was claimed by another process.
     * <p>
     * The token will be claimed by the current process (JVM instance), preventing access by other instances. To release
     * the claim, use {@link #releaseClaim(String, int, ProcessingContext)}
     *
     * @param processorName The process name for which to fetch the token.
     * @param segment       The segment for which to fetch the token.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} with the last stored {@link TrackingToken token} or {@code null} if the store
     * holds no token for the given process and segment.
     */
    @Nonnull
    default CompletableFuture<TrackingToken> fetchToken(
            @Nonnull String processorName,
            @Nonnull Segment segment,
            @Nullable ProcessingContext context
    ) {
        return fetchToken(processorName, segment.getSegmentId(), context);
    }

    /**
     * Extends the claim on the current token held by this node for the given {@code processorName} and
     * {@code segment}.
     * <p>
     * Returns a {@link CompletableFuture} that completes when the claim has been successfully extended.
     * The returned future will complete exceptionally with an {@link UnableToClaimTokenException} if
     * the token did not exist or was claimed by another process.
     *
     * @param processorName The process name for which to fetch the token.
     * @param segmentId     The segment index for which to fetch the token.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} that completes when the claim-extension has been completed.
     * @implSpec By default, this method invokes {@link #fetchToken(String, int, ProcessingContext)}, which also extends
     * the claim if the token is held. Token store implementations may choose to implement this method if they can
     * provide a more efficient way of extending this claim.
     */
    @Nonnull
    default CompletableFuture<Void> extendClaim(
            @Nonnull String processorName,
            int segmentId,
            @Nullable ProcessingContext context
    ) {
        return fetchToken(processorName, segmentId, context).thenRun(() -> {
        });
    }

    /**
     * Release a claim of the token for given {@code processorName} and {@code segment}. If no such claim existed,
     * nothing happens.
     * <p>
     * The caller must ensure not to use any streams opened based on the token for which the claim is released.
     *
     * @param processorName The name of the process owning the token (e.g. a PooledStreamingEventProcessor name).
     * @param segmentId     The segment for which a token was obtained.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} that completes when the claim-release has been completed.
     */
    @Nonnull
    CompletableFuture<Void> releaseClaim(@Nonnull String processorName,
                                         int segmentId,
                                         @Nullable ProcessingContext context);

    /**
     * Initializes a segment with given {@code segment} for the processor with given {@code processorName} to contain
     * the given {@code token}.
     * <p>
     * Returns a {@link CompletableFuture} that completes when the token segment has been successfully initialized.
     * The returned future will complete exceptionally with an {@link UnableToInitializeTokenException} if
     * the token already exists.
     * <p>
     * This method fails if a token already exists for the given processor and segment, even if that token has been
     * claimed by the active instance. This method will not claim the initialized segment. Use
     * {@link #fetchToken(String, int, ProcessingContext)} to retrieve and claim the token.
     *
     * @param token         The token to initialize the segment with.
     * @param processorName The name of the processor to create the segment for.
     * @param segment       The segment to initialize.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} that completes when the segment has been initialized.
     * @throws UnsupportedOperationException If this implementation does not support explicit initialization.
     */
    @Nonnull
    CompletableFuture<Void> initializeSegment(
            @Nullable TrackingToken token,
            @Nonnull String processorName,
            @Nonnull Segment segment,
            @Nullable ProcessingContext context
    );

    /**
     * Deletes the token associated with the specified {@code processorName} and {@code segmentId}.
     * <p>
     * Returns a {@link CompletableFuture} that completes when the token has been successfully deleted.
     * The returned future will complete exceptionally with an {@link UnableToClaimTokenException} if
     * the token is not currently claimed by this node.
     * <p>
     * The token <b>must</b> be owned by the current process (JVM instance) to be able to delete it.
     *
     * @param processorName The name of the processor to remove the token for.
     * @param segmentId     The segment to delete.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} that completes when the token deletion is complete.
     * @throws UnsupportedOperationException If this operation is not supported by this implementation.
     */
    @Nonnull
    CompletableFuture<Void> deleteToken(@Nonnull String processorName,
                                        int segmentId,
                                        @Nullable ProcessingContext context);

    /**
     * Returns a {@link CompletableFuture} that supplies the specified {@link Segment}, or {@code null}
     * if there was no such segment.
     *
     * @param processorName The process name for which to fetch the segment.
     * @param segmentId     The segment index to fetch
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} with the segment, or {@code null} on completion
     */
    @Nonnull
    CompletableFuture<Segment> fetchSegment(@Nonnull String processorName, int segmentId, @Nullable ProcessingContext context);

    /**
     * Returns a {@link CompletableFuture} that supplies a list of known {@link Segment segments} for a
     * given {@code processorName} on completion.
     * <p>
     * The segments returned are segments for which a token has been stored previously. When the {@code TokenStore} is
     * empty, the {@link CompletableFuture} will return an empty array.
     *
     * @param processorName The process name for which to fetch the segments.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} with a list of segments on completion.
     */
    @Nonnull
    CompletableFuture<List<Segment>> fetchSegments(@Nonnull String processorName, @Nullable ProcessingContext context);

    /**
     * Returns a {@link CompletableFuture} supplying a {@code List} of known <b>available</b> {@link Segment Segments}
     * for a given {@code processorName}.
     * <p>
     * A segment is considered available if it is not claimed by any other event processor.
     * <p>
     * The segments returned are segments for which a token has been stored previously and have not been claimed by
     * another processor. When the {@code TokenStore} is empty, an empty list is returned.
     *
     * @param processorName The processor's name for which to fetch the segments.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code List} of available {@link Segment Segments} for the specified {@code processorName}.
     */
    @Nonnull
    CompletableFuture<List<Segment>> fetchAvailableSegments(@Nonnull String processorName,
                                                            @Nullable ProcessingContext context);

    /**
     * Retrieves the storage identifier associated with this store. The returned identifier uniquely identifies
     * the storage location for the tokens in this store.
     * <p>
     * Returns a {@link CompletableFuture} that completes with the storage identifier associated with this store.
     * The returned future will complete exceptionally with an {@link UnableToRetrieveIdentifierException}
     * if the identifier could not be retrieved.
     * <p>
     * Two token store implementations that share state <b>must</b> return the same identifier. Two token store
     * implementations that do not share a location must return a different identifier (or an empty optional if
     * identifiers are not supported).
     * <p>
     * Note that this method may require the implementation to consult its underlying storage. Therefore, a transaction
     * should be active when this method is called, similarly to invocations like
     * {@link #fetchToken(String, int, ProcessingContext)}, {@link #fetchSegments(String, ProcessingContext)}, etc. When
     * no transaction is active, the behavior is undefined.
     *
     * @param context The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} that provides an identifier to uniquely identify the storage location of
     * tokens in this {@code TokenStore} on completion.
     */
    @Nonnull
    CompletableFuture<String> retrieveStorageIdentifier(@Nullable ProcessingContext context);
}