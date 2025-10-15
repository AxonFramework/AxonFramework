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
import org.axonframework.common.FutureUtils;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.axonframework.common.FutureUtils.joinAndUnwrap;

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
     * Returns a {@link CompletableFuture} that initializes the given {@code segmentCount} as the number of segments for
     * the given {@code processorName} to track its tokens.
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
     * @throws UnableToClaimTokenException When a segment has already been created.
     */
    @Nonnull
    default CompletableFuture<Void> initializeTokenSegments(
            @Nonnull String processorName,
            int segmentCount,
            @Nullable TrackingToken initialToken,
            @Nullable ProcessingContext context
    ) throws UnableToClaimTokenException {
        try {
            for (int segment = 0; segment < segmentCount; segment++) {
                joinAndUnwrap(storeToken(initialToken, processorName, segment, context));
                releaseClaim(processorName, segment, context).join();
            }
            return FutureUtils.emptyCompletedFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Asynchronously stores the given {@code token} in the store.
     * <p>
     * The token marks the current position of the process with given {@code processorName} and {@code segment}. The
     * given {@code token} may be {@code null}. Any claims made by the current process have their timestamp updated.
     * <p>
     * This method should throw an {@code UnableToClaimTokenException} when the given {@code segment} has not been
     * initialized with a Token (albeit {@code null}) yet. In that case, a segment must have been explicitly
     * initialized.
     *
     * @param token         The token to store for a given process and segment. May be {@code null}.
     * @param processorName The name of the process for which to store the token.
     * @param segment       The index of the segment for which to store the token.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} that completes when the token has been stored.
     * @throws UnableToClaimTokenException When the token being updated has been claimed by another process. Since this
     *                                     method works asynchronously, the thrown exception is wrapped by a
     *                                     {@link java.util.concurrent.CompletionException}.
     */
    @Nonnull
    CompletableFuture<Void> storeToken(@Nullable TrackingToken token,
                                       @Nonnull String processorName,
                                       int segment,
                                       @Nullable ProcessingContext context) throws UnableToClaimTokenException;

    /**
     * Returns a {@link CompletableFuture} that supplies the last stored {@link TrackingToken token} for the given
     * {@code processorName} and {@code segment} on completion. The {@link CompletableFuture} will return {@code null}
     * if the stored token for the given process and segment is {@code null}.
     * <p>
     * This method should throw an {@code UnableToClaimTokenException} when the given {@code segment} has not been
     * initialized with a Token (albeit {@code null}) yet. In that case, a segment must have been explicitly
     * initialized.
     * <p>
     * The token will be claimed by the current process (JVM instance), preventing access by other instances. To release
     * the claim, use {@link #releaseClaim(String, int, ProcessingContext)}
     *
     * @param processorName The process name for which to fetch the token.
     * @param segment       The segment index for which to fetch the token.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} with the last stored TrackingToken or {@code null} if the store holds no
     * token for the given process and segment. It completes immediately when the token is available.
     * @throws UnableToClaimTokenException If there is a token for given {@code processorName} and {@code segment}, but
     *                                     they are claimed by another process. Since this method works asynchronously,
     *                                     the thrown exception is wrapped by a
     *                                     {@link java.util.concurrent.CompletionException}.
     */
    @Nonnull
    CompletableFuture<TrackingToken> fetchToken(@Nonnull String processorName,
                                                int segment,
                                                @Nullable ProcessingContext context) throws UnableToClaimTokenException;

    /**
     * Returns a {@link CompletableFuture} that supplies the last stored {@link TrackingToken token} for the given
     * {@code processorName} and {@code segment} on completion.
     * <p>
     * The {@link CompletableFuture} will return {@code null} if the stored token for the given process and segment is
     * {@code null}.
     * <p>
     * This method should throw an {@code UnableToClaimTokenException} when the given {@code segment} has not been
     * initialized with a Token (albeit {@code null}) yet. In that case, a segment must have been explicitly
     * initialized.
     * <p>
     * The token will be claimed by the current process (JVM instance), preventing access by other instances. To release
     * the claim, use {@link #releaseClaim(String, int, ProcessingContext)}
     *
     * @param processorName The process name for which to fetch the token.
     * @param segment       The segment for which to fetch the token.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} with the last stored {@link TrackingToken token} or {@code null} if the store
     * holds no token for the given process and segment. It completes immediately when the token is available.
     * @throws UnableToClaimTokenException If there is a token for given {@code processorName} and {@code segment}, but
     *                                     they are claimed by another process, or if the
     *                                     {@code segment has been split or merged concurrently}. Since this method
     *                                     works asynchronously, the thrown exception is wrapped by a
     *                                     {@link java.util.concurrent.CompletionException}.
     */
    @Nonnull
    default CompletableFuture<TrackingToken> fetchToken(
            @Nonnull String processorName,
            @Nonnull Segment segment,
            @Nullable ProcessingContext context
    ) throws UnableToClaimTokenException {
        return fetchToken(processorName, segment.getSegmentId(), context);
    }

    /**
     * Extends the claim on the current token held by this node for the given {@code processorName} and
     * {@code segment}.
     *
     * @param processorName The process name for which to fetch the token.
     * @param segment       The segment index for which to fetch the token.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} that completes when the claim-extension has been completed.
     * @throws UnableToClaimTokenException If there is no token for given {@code processorName} and {@code segment}, or
     *                                     if it has been claimed by another process. Since this method works
     *                                     asynchronously, the thrown exception is wrapped by a
     *                                     {@link java.util.concurrent.CompletionException}.
     * @implSpec By default, this method invokes {@link #fetchToken(String, int, ProcessingContext)}, which also extends
     * the claim if the token is held. Token store implementations may choose to implement this method if they can
     * provide a more efficient way of extending this claim.
     */
    @Nonnull
    default CompletableFuture<Void> extendClaim(
            @Nonnull String processorName,
            int segment,
            @Nullable ProcessingContext context
    ) throws UnableToClaimTokenException {
        return fetchToken(processorName, segment, context).thenRun(() -> {
        });
    }

    /**
     * Release a claim of the token for given {@code processorName} and {@code segment}. If no such claim existed,
     * nothing happens.
     * <p>
     * The caller must ensure not to use any streams opened based on the token for which the claim is released.
     *
     * @param processorName The name of the process owning the token (e.g. a PooledStreamingEventProcessor name).
     * @param segment       the segment for which a token was obtained.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code CompletableFuture} that completes when the claim-release has been completed.
     */
    @Nonnull
    CompletableFuture<Void> releaseClaim(@Nonnull String processorName,
                                         int segment,
                                         @Nullable ProcessingContext context);

    /**
     * Initializes a segment with given {@code segment} for the processor with given {@code processorName} to contain
     * the given {@code token}.
     * <p>
     * This method fails if a token already exists for the given processor and segment, even if that token has been
     * claimed by the active instance. This method will not claim the initialized segment. Use
     * {@link #fetchToken(String, int, ProcessingContext)} to retrieve and claim the token.
     *
     * @param token         The token to initialize the segment with.
     * @param processorName The name of the processor to create the segment for.
     * @param segment       The identifier of the segment to initialize.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} that completes when the segment has been initialized.
     * @throws UnableToInitializeTokenException If a token already exists.
     * @throws UnsupportedOperationException    If this implementation does not support explicit initialization.
     */
    @Nonnull
    CompletableFuture<Void> initializeSegment(
            @Nullable TrackingToken token,
            @Nonnull String processorName,
            int segment,
            @Nullable ProcessingContext context
    ) throws UnableToInitializeTokenException;

    /**
     * Deletes the token for the processor with given {@code processorName} and {@code segment}.
     * <p>
     * The token <b>must</b> be owned by the current process (JVM instance) to be able to delete it.
     *
     * @param processorName The name of the processor to remove the token for.
     * @param segment       The segment to delete.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} that completes when the token has been deleted.
     * @throws UnableToClaimTokenException   If the token is not currently claimed by this node.
     * @throws UnsupportedOperationException If this operation is not supported by this implementation.
     */
    @Nonnull
    CompletableFuture<Void> deleteToken(@Nonnull String processorName,
                                        int segment,
                                        @Nullable ProcessingContext context) throws UnableToClaimTokenException;

    /**
     * Returns a {@link CompletableFuture} that supplies an array of identifiers of the known {@code segments} for a
     * given {@code processorName} on completion.
     * <p>
     * The segments returned are segments for which a token has been stored previously. When the {@code TokenStore} is
     * empty, the {@link CompletableFuture} will return an empty array.
     *
     * @param processorName The process name for which to fetch the segments.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@link CompletableFuture} that results to an array of segment identifiers on completion.
     */
    @Nonnull
    CompletableFuture<int[]> fetchSegments(@Nonnull String processorName, @Nullable ProcessingContext context);

    /**
     * Returns a {@link CompletableFuture} supplying a {@code List} of known <b>available</b> {@link Segment Segments}
     * for a given {@code processorName}.
     * <p>
     * A segment is considered available if it is not claimed by any other event processor.
     * <p>
     * The segments returned are segments for which a token has been stored previously and have not been claimed by
     * another processor. When the {@code TokenStore} is empty, an empty list is returned. By default, if this method is
     * not implemented, we will return all segments instead, whether they are available or not.
     *
     * @param processorName The processor's name for which to fetch the segments.
     * @param context       The current {@link ProcessingContext}, if any.
     * @return A {@code List} of available {@link Segment Segments} for the specified {@code processorName}.
     */
    @Nonnull
    default CompletableFuture<List<Segment>> fetchAvailableSegments(@Nonnull String processorName,
                                                                    @Nullable ProcessingContext context) {
        return fetchSegments(processorName, context)
                .thenApply(segments -> Arrays.stream(segments).boxed()
                                             .map(segment -> Segment.computeSegment(segment, segments))
                                             .collect(Collectors.toList()));
    }

    /**
     * Returns a unique identifier that uniquely identifies the storage location of the tokens in this store.
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
     * @throws UnableToRetrieveIdentifierException When the implementation was unable to determine its identifier.
     */
    @Nonnull
    default CompletableFuture<Optional<String>> retrieveStorageIdentifier(
            @Nullable ProcessingContext context
    ) throws UnableToRetrieveIdentifierException {
        return completedFuture(Optional.empty());
    }
}