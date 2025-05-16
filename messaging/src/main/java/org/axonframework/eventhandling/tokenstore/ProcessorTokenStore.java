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

package org.axonframework.eventhandling.tokenstore;

import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Describes a component capable of storing and retrieving event tracking tokens for a specific event processor.
 * An event processor tracking an event stream can use the store to keep track of its position in the event stream.
 * Tokens are stored by segment index, enabling the same processor to be distributed over multiple processes or machines.
 *
 * @author Mateusz Nowak
 * @since 5.0
 */
public interface ProcessorTokenStore {

    /**
     * Initializes the given {@code segmentCount} number of segments to track its tokens.
     * This method should only be invoked when no tokens have been stored yet.
     * <p>
     * This method will initialize the tokens, but not claim them. It will create the segments ranging from {@code 0}
     * until {@code segmentCount - 1}.
     *
     * @param processingContext The context in which the segments are being initialized
     * @param segmentCount  The number of segments to initialize
     * @return A CompletableFuture that completes when all segments are initialized
     * @throws UnableToClaimTokenException when a segment has already been created
     */
    CompletableFuture<Void> initializeTokenSegments(@Nonnull ProcessingContext processingContext, int segmentCount);

    /**
     * Initializes the given {@code segmentCount} number of segments to track its tokens.
     * This method should only be invoked when no tokens have been stored yet.
     * <p>
     * This method will store {@code initialToken} for all segments as starting point, but not claim them.
     * It will create the segments ranging from {@code 0} until {@code segmentCount - 1}.
     *
     * @param processingContext The context in which the segments are being initialized
     * @param segmentCount  The number of segments to initialize
     * @param initialToken  The initial token which is used as a starting point
     * @return A CompletableFuture that completes when all segments are initialized
     * @throws UnableToClaimTokenException when a segment has already been created
     */
    CompletableFuture<Void> initializeTokenSegments(@Nonnull ProcessingContext processingContext,
                                                    int segmentCount,
                                                    @Nullable TrackingToken initialToken);


    /**
     * Stores the given {@code token} in the store. The token marks the current position of the segment with given
     * {@code segmentId}. The given {@code token} may be {@code null}.
     *
     * @param processingContext The context in which the token is being stored
     * @param token         The token to store for a given segment. May be {@code null}.
     * @param segmentId     The index of the segment for which to store the token
     * @return A CompletableFuture that completes when the token is stored
     * @throws UnableToClaimTokenException when the token being updated has been claimed by another process.
     */
    CompletableFuture<Void> storeToken(@Nonnull ProcessingContext processingContext,
                                       @Nullable TrackingToken token,
                                       int segmentId);

    /**
     * Returns the last stored {@link TrackingToken token} for the given {@code segmentId}.
     * Returns {@code null} if the stored token for the given segment is {@code null}.
     * <p>
     * The token will be claimed by the current process, preventing access by other instances. To release
     * the claim, use {@link #releaseClaim(ProcessingContext, int)}
     *
     * @param processingContext The context in which the token is being fetched
     * @param segmentId     The segment index for which to fetch the token
     * @return A CompletableFuture containing the last stored TrackingToken or {@code null} if the store holds no token for the given segment
     * @throws UnableToClaimTokenException if there is a token for the given {@code segmentId}, but
     *                                  it is claimed by another process.
     */
    CompletableFuture<TrackingToken> fetchToken(@Nonnull ProcessingContext processingContext, int segmentId);

    /**
     * Returns the last stored {@link TrackingToken token} for the given {@code segment}. Returns {@code null} if the stored token for
     * the given segment is {@code null}.
     * <p>
     * The token will be claimed by the current process, preventing access by other instances. To release the claim, use {@link
     * #releaseClaim(ProcessingContext, int)}
     *
     * @param processingContext The context in which the token is being fetched
     * @param segment       The segment for which to fetch the token
     * @return A CompletableFuture containing the last stored TrackingToken or {@code null} if the store holds no token for the given segment
     * @throws UnableToClaimTokenException if there is a token for the given {@code segment}, but it is claimed by another process, or
     *                                    if the {@code segment has been split or merged concurrently}
     */
    CompletableFuture<TrackingToken> fetchToken(@Nonnull ProcessingContext processingContext, @Nonnull Segment segment);

    /**
     * Release a claim of the token for given {@code segmentId}. If no such claim existed,
     * nothing happens.
     * <p>
     * The caller must ensure not to use any streams opened based on the token for which the claim is released.
     *
     * @param processingContext The context in which the claim is being released
     * @param segmentId     the segment for which a token was obtained
     * @return A CompletableFuture that completes when the claim is released
     */
    CompletableFuture<Void> releaseClaim(@Nonnull ProcessingContext processingContext, int segmentId);

    /**
     * Initializes a segment with given {@code segmentId} to contain the given {@code token}.
     * <p>
     * This method fails if a Token already exists for the given segment, even if that token has been
     * claimed by the active instance.
     * <p>
     * This method will not claim the initialized segment. Use {@link #fetchToken(ProcessingContext, int)} to retrieve and claim
     * the token.
     *
     * @param processingContext The context in which the segment is being initialized
     * @param token         The token to initialize the segment with
     * @param segmentId     The identifier of the segment to initialize
     * @return A CompletableFuture that completes when the segment is initialized
     * @throws UnableToInitializeTokenException if a Token already exists
     */
    CompletableFuture<Void> initializeSegment(@Nonnull ProcessingContext processingContext,
                                              @Nullable TrackingToken token,
                                              int segmentId);

    /**
     * Deletes the token for the given {@code segmentId}. The token must be owned by the current process
     * to be able to delete it.
     *
     * @param processingContext The context in which the token is being deleted
     * @param segmentId     The segment to delete
     * @return A CompletableFuture that completes when the token is deleted
     * @throws UnableToClaimTokenException if the token is not currently claimed by this process
     */
    CompletableFuture<Void> deleteToken(@Nonnull ProcessingContext processingContext, int segmentId);

    /**
     * Returns an array of known {@code segments}.
     * <p>
     * The segments returned are segments for which a token has been stored previously. When the TokenStore is
     * empty, an empty array is returned.
     *
     * @param processingContext The context in which the segments are being fetched
     * @return A CompletableFuture containing an array of segment identifiers
     */
    CompletableFuture<int[]> fetchSegments(@Nonnull ProcessingContext processingContext);

    /**
     * Returns a List of known available {@code segments}. A segment is considered available if it is not claimed by any
     * other event processor.
     * <p>
     * The segments returned are segments for which a token has been stored previously and have not been claimed by another processor. When the
     * store is empty, an empty list is returned.
     *
     * @param processingContext The context in which the available segments are being fetched
     * @return A CompletableFuture containing a List of available segment identifiers
     */
    CompletableFuture<List<Segment>> fetchAvailableSegments(@Nonnull ProcessingContext processingContext);

    /**
     * Returns a unique identifier that uniquely identifies the storage location of the tokens in this store. Two token
     * store implementations that share state, must return the same identifier. Two token store implementations that
     * do not share a location, must return a different identifier (or an empty optional if identifiers are not
     * supported).
     *
     * @param processingContext The context in which the storage identifier is being retrieved
     * @return A CompletableFuture containing an identifier to uniquely identify the storage location of tokens in this TokenStore
     * @throws UnableToRetrieveIdentifierException when the implementation was unable to determine its identifier
     */
    CompletableFuture<Optional<String>> retrieveStorageIdentifier(@Nonnull ProcessingContext processingContext);
}