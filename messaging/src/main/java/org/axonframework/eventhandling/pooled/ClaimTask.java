/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.pooled;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.MergedTrackingToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * A {@link CoordinatorTask} implementation dedicated to claiming a token so that the {@link Coordinator} can
 * start a new {@link WorkPackage} in its next cycle.
 * <p>
 * It achieves this by removing the segment from the {@code releasesDeadlines} map which prevent the segment from
 * being claimed if a previous release operation was done, and claiming the token in the store.
 * Upon the next iteration of the {@link Coordinator}, it will see the segment is claimed and will start a new
 * {@link WorkPackage} for it.
 *
 * @author Mitchell Herrijgers
 * @see Coordinator
 * @since 4.9.0
 */
class ClaimTask extends CoordinatorTask {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final String name;
    private final int segmentId;
    private final Map<Integer, WorkPackage> workPackages;
    private final Map<Integer, Instant> releasesDeadlines;
    private final TokenStore tokenStore;
    private final TransactionManager transactionManager;

    /**
     * Constructs a {@link ClaimTask}.
     *
     * @param result             the {@link CompletableFuture} to {@link #complete(Boolean, Throwable)} once {@link
     *                           #run()} has finalized
     * @param name               the name of the {@link Coordinator} this instruction will be ran in. Used to correctly
     *                           deal with the {@code tokenStore}
     * @param segmentId          the identifier of the {@link Segment} this instruction should merge
     * @param workPackages       the collection of {@link WorkPackage}s controlled by the {@link Coordinator}. Will be
     *                           queried for the presence of the given {@code segmentId} and the segment to merge it
     *                           with
     * @param releasesDeadlines  the map of release deadlines for each segment
     * @param tokenStore         the storage solution for {@link TrackingToken}s. Used to claim the {@code segmentId} if
     *                           it is not present in the {@code workPackages}, to remove one of the segments and merge
     *                           the merged token
     * @param transactionManager a {@link TransactionManager} used to invoke all {@link TokenStore} operations inside a
     */
    ClaimTask(CompletableFuture<Boolean> result,
              String name,
              int segmentId,
              Map<Integer, WorkPackage> workPackages,
              Map<Integer, Instant> releasesDeadlines,
              TokenStore tokenStore,
              TransactionManager transactionManager) {
        super(result, name);
        this.name = name;
        this.segmentId = segmentId;
        this.workPackages = workPackages;
        this.releasesDeadlines = releasesDeadlines;
        this.transactionManager = transactionManager;
        this.tokenStore = tokenStore;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs a {@link Segment} claim. Will succeed if the {@link TrackingToken} respective for that segment is
     * claimable and has been successfully claimed.
     */
    @Override
    protected CompletableFuture<Boolean> task() {
        logger.debug("Processor [{}] will perform claim instruction for segment {}.", name, segmentId);

        if (workPackages.containsKey(segmentId)) {
            return CompletableFuture.completedFuture(true);
        }
        releasesDeadlines.remove(segmentId);
        List<Segment> segments = transactionManager.fetchInTransaction(() -> tokenStore.fetchAvailableSegments(name));

        Optional<Segment> segmentToClaim = segments.stream()
                .filter(segment -> segment.getSegmentId() == segmentId)
                .findFirst();
        if (!segmentToClaim.isPresent()) {
            logger.info("Processor [{}] cannot claim segment {}. It is not available.", name, segmentId);
            return CompletableFuture.completedFuture(false);
        }

        try {
            transactionManager.fetchInTransaction(() -> tokenStore.fetchToken(name, segmentId));
        } catch (Exception e) {
            logger.warn("Processor [{}] cannot claim segment {} due to an error.", name, segmentId, e);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.completedFuture(true);
    }

    @Override
    String getDescription() {
        return "Claim Task for segment " + segmentId;
    }
}
