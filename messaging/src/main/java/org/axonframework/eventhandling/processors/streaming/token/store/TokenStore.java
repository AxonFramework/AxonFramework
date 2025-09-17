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
import org.axonframework.eventhandling.processors.EventProcessor;
import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.axonframework.common.FutureUtils.joinAndUnwrap;

/**
 * Describes a component capable of storing and retrieving event tracking tokens. An {@link EventProcessor} that is
 * tracking an event stream can use the store to keep track of its position in the event stream. Tokens are stored by
 * process name and segment index, enabling the same processor to be distributed over multiple processes or machines.
 *
 * @author Rene de Waele
 * @author Allard Buijze
 */
public interface TokenStore {

    /**
     * Initializes the given {@code segmentCount} number of segments for the given {@code processorName} to track its
     * tokens. This method should only be invoked when no tokens have been stored for the given processor, yet.
     * <p>
     * This method will initialize the tokens but not claim them. It will create the segments ranging from {@code 0}
     * until {@code segmentCount - 1}.
     * <p>
     * The exact behavior when this method is called while tokens were already present is undefined in case the token
     * already present is not owned by the initializing process.
     *
     * @param processorName     The name of the processor to initialize segments for
     * @param segmentCount      The number of segments to initialize
     * @param processingContext The processing context to use when initializing the segments.
     * @throws UnableToClaimTokenException when a segment has already been created
     */
    default void initializeTokenSegments(@Nonnull String processorName,
                                         int segmentCount,
                                         @Nonnull ProcessingContext processingContext)
            throws UnableToClaimTokenException {
        for (int segment = 0; segment < segmentCount; segment++) {
            fetchToken(processorName, segment);
            releaseClaim(processorName, segment);
        }
    }

    /**
     * Initializes the given {@code segmentCount} number of segments for the given {@code processorName} to track its
     * tokens. This method should only be invoked when no tokens have been stored for the given processor, yet.
     * <p>
     * This method will store {@code initialToken} for all segments as starting point for processor, but not claim them.
     * It will create the segments ranging from {@code 0} until {@code segmentCount - 1}.
     * <p>
     * The exact behavior when this method is called while tokens were already present is undefined in case the token
     * already present is not owned by the initializing process.
     *
     * @param processorName     The name of the processor to initialize segments for
     * @param segmentCount      The number of segments to initialize
     * @param initialToken      The initial token which is used as a starting point for the processor
     * @param processingContext The processing context to use when initializing the segments
     * @throws UnableToClaimTokenException when a segment has already been created
     */
    default void initializeTokenSegments(@Nonnull String processorName,
                                         int segmentCount,
                                         @Nullable TrackingToken initialToken,
                                         @Nonnull ProcessingContext processingContext)
            throws UnableToClaimTokenException {
        for (int segment = 0; segment < segmentCount; segment++) {
            joinAndUnwrap(storeToken(initialToken, processorName, segment, processingContext));
            releaseClaim(processorName, segment);
        }
    }

    /**
     * Asynchronously stores the given {@code token} in the store. The token marks the current position of the process
     * with given {@code processorName} and {@code segment}. The given {@code token} may be {@code null}.
     * <p/>
     * Any claims made by the current process have their timestamp updated.
     * <p>
     * This method should throw an {@code UnableToClaimTokenException} when the given {@code segment} has not been
     * initialized with a Token (albeit {@code null}) yet. In that case, a segment must have been explicitly
     * initialized. A TokenStore implementation's ability to do so is exposed by the
     * {@link #requiresExplicitSegmentInitialization()} method. If that method returns false, this method may implicitly
     * initialize a token and return that token upon invocation.
     *
     * @param token             The token to store for a given process and segment. May be {@code null}.
     * @param processorName     The name of the process for which to store the token
     * @param segment           The index of the segment for which to store the token
     * @param processingContext The current {@link ProcessingContext}
     * @return CompletableFuture that completes when the token has been stored.
     * @throws UnableToClaimTokenException when the token being updated has been claimed by another process.
     */
    CompletableFuture<Void> storeToken(@Nullable TrackingToken token,
                                       @Nonnull String processorName,
                                       int segment,
                                       @Nonnull ProcessingContext processingContext)
            throws UnableToClaimTokenException;

    /**
     * Returns the last stored {@link TrackingToken token} for the given {@code processorName} and {@code segment}.
     * Returns {@code null} if the stored token for the given process and segment is {@code null}.
     * <p>
     * This method should throw an {@code UnableToClaimTokenException} when the given {@code segment} has not been
     * initialized with a Token (albeit {@code null}) yet. In that case, a segment must have been explicitly
     * initialized. A TokenStore implementation's ability to do so is exposed by the
     * {@link #requiresExplicitSegmentInitialization()} method. If that method returns false, this method may implicitly
     * initialize a token and return that token upon invocation.
     * <p>
     * The token will be claimed by the current process (JVM instance), preventing access by other instances. To release
     * the claim, use {@link #releaseClaim(String, int)}
     *
     * @param processorName The process name for which to fetch the token
     * @param segment       The segment index for which to fetch the token
     * @return CompletableFuture with the last stored TrackingToken or {@code null} if the store holds no token for the
     * given process and segment. It completes immediately when the token is available.
     * @throws UnableToClaimTokenException if there is a token for given {@code processorName} and {@code segment}, but
     *                                     they are claimed by another process.
     */
    CompletableFuture<TrackingToken> fetchToken(@Nonnull String processorName, int segment)
            throws UnableToClaimTokenException;

    /**
     * Returns the last stored {@link TrackingToken token} for the given {@code processorName} and {@code segment}.
     * Returns {@code null} if the stored token for the given process and segment is {@code null}.
     * <p>
     * This method should throw an {@code UnableToClaimTokenException} when the given {@code segment} has not been
     * initialized with a Token (albeit {@code null}) yet. In that case, a segment must have been explicitly
     * initialized. A TokenStore implementation's ability to do so is exposed by the
     * {@link #requiresExplicitSegmentInitialization()} method. If that method returns false, this method may implicitly
     * initialize a token and return that token upon invocation.
     * <p>
     * The token will be claimed by the current process (JVM instance), preventing access by other instances. To release
     * the claim, use {@link #releaseClaim(String, int)}
     *
     * @param processorName The process name for which to fetch the token
     * @param segment       The segment for which to fetch the token
     * @return CompletableFuture with the last stored TrackingToken or {@code null} if the store holds no token for the
     * given process and segment. It completes immediately when the token is available.
     * @throws UnableToClaimTokenException if there is a token for given {@code processorName} and {@code segment}, but
     *                                     they are claimed by another process, or if the
     *                                     {@code segment has been split or merged concurrently}
     */
    default CompletableFuture<TrackingToken> fetchToken(@Nonnull String processorName, @Nonnull Segment segment)
            throws UnableToClaimTokenException {
        return fetchToken(processorName, segment.getSegmentId());
    }

    /**
     * Extends the claim on the current token held by the this node for the given {@code processorName} and
     * {@code segment}.
     *
     * @param processorName The process name for which to fetch the token
     * @param segment       The segment index for which to fetch the token
     * @throws UnableToClaimTokenException if there is no token for given {@code processorName} and {@code segment}, or
     *                                     if it has been claimed by another process.
     * @implSpec By default, this method invokes {@link #fetchToken(String, int)}, which also extends the claim if the
     * token is held. TokenStore implementations may choose to implement this method if they can provide a more
     * efficient way of extending this claim.
     */
    default void extendClaim(@Nonnull String processorName, int segment) throws UnableToClaimTokenException {
        fetchToken(processorName, segment);
    }

    /**
     * Release a claim of the token for given {@code processorName} and {@code segment}. If no such claim existed,
     * nothing happens.
     * <p>
     * The caller must ensure not to use any streams opened based on the token for which the claim is released.
     *
     * @param processorName The name of the process owning the token (e.g. a PooledStreamingEventProcessor name)
     * @param segment       the segment for which a token was obtained
     */
    void releaseClaim(@Nonnull String processorName, int segment);

    /**
     * Initializes a segment with given {@code segment} for the processor with given {@code processorName} to contain
     * the given {@code token}.
     * <p>
     * This method fails if a Token already exists for the given processor and segment, even if that token has been
     * claimed by the active instance.
     * <p>
     * This method will not claim the initialized segment. Use {@link #fetchToken(String, int)} to retrieve and claim
     * the token.
     *
     * @param token         The token to initialize the segment with
     * @param processorName The name of the processor to create the segment for
     * @param segment       The identifier of the segment to initialize
     * @throws UnableToInitializeTokenException if a Token already exists
     * @throws UnsupportedOperationException    if this implementation does not support explicit initialization. See
     *                                          {@link #requiresExplicitSegmentInitialization()}.
     */
    default void initializeSegment(@Nullable TrackingToken token, @Nonnull String processorName, int segment)
            throws UnableToInitializeTokenException {
        throw new UnsupportedOperationException(
                "Explicit initialization is not supported by this TokenStore implementation");
    }

    /**
     * Deletes the token for the processor with given {@code processorName} and {@code segment}. The token must be owned
     * by the current node, to be able to delete it.
     * <p>
     * Implementations should implement this method only when {@link #requiresExplicitSegmentInitialization()} is
     * overridden to return {@code true}. Deleting tokens using implementations that do not require explicit token
     * initialization is unsafe, as a claim will automatically recreate the deleted token instance, which may result in
     * concurrency issues.
     *
     * @param processorName The name of the processor to remove the token for
     * @param segment       The segment to delete
     * @throws UnableToClaimTokenException   if the token is not currently claimed by this node
     * @throws UnsupportedOperationException if this operation is not supported by this implementation
     */
    default void deleteToken(@Nonnull String processorName, int segment) throws UnableToClaimTokenException {
        throw new UnsupportedOperationException(
                "Explicit initialization (which is required to reliably delete tokens) is not supported by this TokenStore implementation");
    }

    /**
     * Indicates whether this TokenStore instance requires segments to be explicitly initialized before any tokens can
     * be claimed for that segment.
     *
     * @return {@code true} if this instance requires tokens to be explicitly initialized, otherwise {@code false}.
     * @see #initializeTokenSegments(String, int, ProcessingContext)
     * @see #initializeTokenSegments(String, int, TrackingToken, ProcessingContext)
     * @see #initializeSegment(TrackingToken, String, int)
     */
    default boolean requiresExplicitSegmentInitialization() {
        return false;
    }

    /**
     * Returns an array of known {@code segments} for a given {@code processorName}.
     * <p>
     * The segments returned are segments for which a token has been stored previously. When the {@link TokenStore} is
     * empty, an empty array is returned.
     *
     * @param processorName The process name for which to fetch the segments
     * @return an array of segment identifiers.
     */
    int[] fetchSegments(@Nonnull String processorName);

    /**
     * Returns a List of known available {@code segments} for a given {@code processorName}. A segment is considered
     * available if it is not claimed by any other event processor.
     * <p>
     * The segments returned are segments for which a token has been stored previously and have not been claimed by
     * another processor. When the {@link TokenStore} is empty, an empty list is returned.
     * <p>
     * By default, if this method is not implemented, we will return all segments instead, whether they are available or
     * not.
     *
     * @param processorName the processor's name for which to fetch the segments
     * @return a List of available segment identifiers for the specified {@code processorName}
     */
    default List<Segment> fetchAvailableSegments(@Nonnull String processorName) {
        int[] allSegments = fetchSegments(processorName);
        return Arrays.stream(allSegments).boxed()
                     .map(segment -> Segment.computeSegment(segment, allSegments))
                     .collect(Collectors.toList());
    }

    /**
     * Returns a unique identifier that uniquely identifies the storage location of the tokens in this store. Two token
     * store implementations that share state, must return the same identifier. Two token store implementations that do
     * not share a location, must return a different identifier (or an empty optional if identifiers are not
     * supported).
     * <p>
     * Note that this method may require the implementation to consult its underlying storage. Therefore, a Transaction
     * should be active when this method is called, similarly to invocations like {@link #fetchToken(String, int)},
     * {@link #fetchSegments(String)}, etc. When no Transaction is active, the behavior is undefined.
     *
     * @return an identifier to uniquely identify the storage location of tokens in this TokenStore.
     * @throws UnableToRetrieveIdentifierException when the implementation was unable to determine its identifier
     */
    default Optional<String> retrieveStorageIdentifier() throws UnableToRetrieveIdentifierException {
        return Optional.empty();
    }
}