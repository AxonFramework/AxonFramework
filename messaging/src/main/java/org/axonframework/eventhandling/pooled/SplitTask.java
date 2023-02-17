/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.eventhandling.pooled;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackerStatus;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link CoordinatorTask} implementation dedicated to splitting a {@link Segment}.
 * <p>
 * If the {@link Coordinator} owning this instruction is currently in charge of the specified segment, the {@link
 * WorkPackage} will be aborted and subsequently its segment split. When the {@code Coordinator} is not in charge of the
 * specified {@code segmentId}, it will try to claim the segment's {@link TrackingToken} and then split it.
 * <p>
 * In either way, the segment's claim (if present) will be released, so that another thread can proceed with processing
 * it.
 *
 * @author Steven van Beelen
 * @see Coordinator
 * @since 4.5
 */
class SplitTask extends CoordinatorTask {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String name;
    private final int segmentId;
    private final Map<Integer, WorkPackage> workPackages;
    private final TokenStore tokenStore;
    private final TransactionManager transactionManager;

    /**
     * Constructs a {@link SplitTask}.
     *
     * @param result             the {@link CompletableFuture} to {@link #complete(Boolean, Throwable)} once {@link
     *                           #run()} has finalized
     * @param name               the name of the {@link Coordinator} this instruction will be ran in. Used to correctly
     *                           deal with the {@code tokenStore}
     * @param segmentId          the identifier of the {@link Segment} this instruction should split
     * @param workPackages       the collection of {@link WorkPackage}s controlled by the {@link Coordinator}. Will be
     *                           queried for the presence of the given {@code segmentId}
     * @param tokenStore         the storage solution for {@link TrackingToken}s. Used to claim the {@code segmentId} if
     *                           it is not present in the {@code workPackages} and to store the split segment
     * @param transactionManager a {@link TransactionManager} used to invoke all {@link TokenStore} operations inside a
     */
    SplitTask(CompletableFuture<Boolean> result,
              String name,
              int segmentId,
              Map<Integer, WorkPackage> workPackages,
              TokenStore tokenStore,
              TransactionManager transactionManager) {
        super(result, name);
        this.name = name;
        this.segmentId = segmentId;
        this.workPackages = workPackages;
        this.tokenStore = tokenStore;
        this.transactionManager = transactionManager;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs a {@link Segment} split. Will succeed if either the given {@code workPackages} contain a {@link
     * WorkPackage} corresponding to the given {@code segmentId} or if the {@link TrackingToken} for the {@code
     * segmentId} can be claimed.
     */
    @Override
    protected CompletableFuture<Boolean> task() {
        logger.debug("Processor [{}] will perform split instruction for segment {}.", name, segmentId);
        // Remove WorkPackage so that the CoordinatorTask cannot find it to release its claim upon impending abortion.
        WorkPackage workPackage = workPackages.remove(segmentId);
        return workPackage != null ? abortAndSplit(workPackage) : fetchSegmentAndSplit(segmentId);
    }

    private CompletableFuture<Boolean> abortAndSplit(WorkPackage workPackage) {
        return workPackage.abort(null)
                          .thenApply(e -> splitAndRelease(workPackage.segment()));
    }

    private CompletableFuture<Boolean> fetchSegmentAndSplit(int segmentId) {
        return CompletableFuture.completedFuture(
                transactionManager.fetchInTransaction(() -> {
                    int[] segments = tokenStore.fetchSegments(name);
                    Segment segmentToSplit = Segment.computeSegment(segmentId, segments);
                    return splitAndRelease(segmentToSplit);
                })
        );
    }

    private boolean splitAndRelease(Segment segmentToSplit) {
        transactionManager.executeInTransaction(() -> {
            TrackingToken tokenToSplit = tokenStore.fetchToken(name, segmentToSplit.getSegmentId());
            TrackerStatus[] splitStatuses = TrackerStatus.split(segmentToSplit, tokenToSplit);
            tokenStore.initializeSegment(
                    splitStatuses[1].getTrackingToken(), name, splitStatuses[1].getSegment().getSegmentId()
            );
            tokenStore.releaseClaim(name, splitStatuses[0].getSegment().getSegmentId());
            logger.info("Processor [{}] successfully split {} into {} and {}.",
                        name, segmentToSplit, splitStatuses[0].getSegment(), splitStatuses[1].getSegment());
        });
        return true;
    }

    @Override
    String getDescription() {
        return "Split Task for segment " + segmentId;
    }
}
