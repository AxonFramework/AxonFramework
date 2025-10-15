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

package org.axonframework.eventhandling.processors.streaming.pooled;

import org.axonframework.eventhandling.processors.streaming.segmenting.Segment;
import org.axonframework.eventhandling.processors.streaming.token.MergedTrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.common.FutureUtils.joinAndUnwrap;

/**
 * A {@link CoordinatorTask} implementation dedicated to merging two {@link Segment}s.
 * <p>
 * If the {@link Coordinator} owning this instruction is currently in charge of the {@code segmentId} and the segment to
 * merge it with, both {@link WorkPackage}s will be aborted, after which the merge will start. When this coordinator is
 * not in charge of one of the two segments, it will try to claim either segment's {@link TrackingToken} and perform the
 * merge then.
 * <p>
 * In either approach, this operation will delete one of the segments and release the claim on the other so that another
 * thread can proceed with processing it.
 *
 * @author Steven van Beelen
 * @see Coordinator
 * @since 4.5
 */
class MergeTask extends CoordinatorTask {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String name;
    private final int segmentId;
    private final Map<Integer, WorkPackage> workPackages;
    private final TokenStore tokenStore;
    private final UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Constructs a {@code MergeTask}.
     *
     * @param result            The {@link CompletableFuture} to {@link #complete(Boolean, Throwable)} once
     *                          {@link #run()} has finalized.
     * @param name              The name of the {@link Coordinator} this instruction will run in. Used to correctly deal
     *                          with the {@code tokenStore}.
     * @param segmentId         The identifier of the {@link Segment} this instruction should merge.
     * @param workPackages      The collection of {@link WorkPackage}s controlled by the {@link Coordinator}. Will be
     *                          queried for the presence of the given {@code segmentId} and the segment to merge it
     *                          with.
     * @param tokenStore        The storage solution for {@link TrackingToken}s. Used to claim the {@code segmentId} if
     *                          it is not present in the {@code workPackages}, to remove one of the segments and merge
     *                          the merged token.
     * @param unitOfWorkFactory The {@link UnitOfWorkFactory} that spawns {@link UnitOfWork UnitOfWorks} used to invoke
     *                          all {@link TokenStore} operations inside a unit of work.
     */
    MergeTask(CompletableFuture<Boolean> result,
              String name,
              int segmentId,
              Map<Integer, WorkPackage> workPackages,
              TokenStore tokenStore,
              UnitOfWorkFactory unitOfWorkFactory) {
        super(result, name);
        this.name = name;
        this.segmentId = segmentId;
        this.workPackages = workPackages;
        this.unitOfWorkFactory = unitOfWorkFactory;
        this.tokenStore = tokenStore;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs a {@link Segment} merge. Will succeed if either the given {@code workPackages} contain the
     * {@link WorkPackage}s corresponding to the given {@code segmentId} and the identifier to merge with. Or, if the
     * {@link TrackingToken}(s) for the segments can be claimed.
     */
    @Override
    protected CompletableFuture<Boolean> task() {
        logger.debug("Processor [{}] will perform merge instruction for segment {}.", name, segmentId);

        int[] segments = joinAndUnwrap(unitOfWorkFactory.create().executeWithResult(
                context -> tokenStore.fetchSegments(name, context)
        ));
        Segment thisSegment = Segment.computeSegment(segmentId, segments);
        int thatSegmentId = thisSegment.mergeableSegmentId();
        Segment thatSegment = Segment.computeSegment(thatSegmentId, segments);

        if (segmentId == thatSegmentId) {
            logger.debug("Processor [{}] cannot merge segment {}. "
                                 + "A merge request can only be fulfilled if there is more than one segment.",
                         name, segmentId);
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<TrackingToken> thisTokenFuture = tokenFor(thisSegment.getSegmentId());
        CompletableFuture<TrackingToken> thatTokenFuture = tokenFor(thatSegment.getSegmentId());
        return thisTokenFuture.thenCombine(
                thatTokenFuture,
                (thisToken, thatToken) -> mergeSegments(thisSegment, thisToken, thatSegment, thatToken)
        );
    }

    private CompletableFuture<TrackingToken> tokenFor(int segmentId) {
        // Remove WorkPackage so that the CoordinatorTask cannot find it to release its claim upon impending abortion.
        return workPackages.containsKey(segmentId)
                ? workPackages.remove(segmentId)
                              .abort(null)
                              .thenCompose(e -> fetchTokenInUnitOfWork(segmentId))
                : fetchTokenInUnitOfWork(segmentId);
    }

    private CompletableFuture<TrackingToken> fetchTokenInUnitOfWork(int segmentId) {
        return unitOfWorkFactory.create().executeWithResult(
                context -> tokenStore.fetchToken(name, segmentId, context)
        );
    }

    private Boolean mergeSegments(Segment thisSegment, TrackingToken thisToken,
                                  Segment thatSegment, TrackingToken thatToken) {
        Segment mergedSegment = thisSegment.mergedWith(thatSegment);
        // We want to keep the token with the segmentId obtained by the merge operation, and to delete the other
        int tokenToDelete = mergedSegment.getSegmentId() == thisSegment.getSegmentId()
                ? thatSegment.getSegmentId() : thisSegment.getSegmentId();
        TrackingToken mergedToken = thatSegment.getSegmentId() < thisSegment.getSegmentId()
                ? MergedTrackingToken.merged(thatToken, thisToken)
                : MergedTrackingToken.merged(thisToken, thatToken);

        joinAndUnwrap(unitOfWorkFactory.create().executeWithResult(
                context -> tokenStore.deleteToken(name, tokenToDelete, context)
                                     .thenCompose(result -> tokenStore.storeToken(mergedToken,
                                                                                  name,
                                                                                  mergedSegment.getSegmentId(),
                                                                                  context))
                                     .thenCompose(result -> tokenStore.releaseClaim(name,
                                                                                    mergedSegment.getSegmentId(),
                                                                                    context))
        ));

        logger.info("Processor [{}] successfully merged {} with {} into {}.",
                    name, thisSegment, thatSegment, mergedSegment);
        return true;
    }

    @Override
    String getDescription() {
        return "Merge Task for segment " + segmentId;
    }
}