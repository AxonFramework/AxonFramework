package org.axonframework.eventhandling.pooled;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.MergedTrackingToken;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link CoordinatorTask} implementation dedicated to merging two {@link Segment}s.
 * <p>
 * If the {@link Coordinator} owning this instruction is currently in charge of the {@code segmentId} and the segment to
 * merge it with, both {@link WorkPackage}s will be aborted, after which the merge will start. When this coordinator is
 * not in charge of one of the two segments, it will try to claim either segment's {@link TrackingToken} and perform the
 * merge then.
 * <p>
 * In either approach, this operation will delete one of the segments and release the claim on the other, so that
 * another thread can proceed with processing it.
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
    private final TransactionManager transactionManager;

    /**
     * Constructs a {@link MergeTask}.
     *
     * @param result             the {@link CompletableFuture} to {@link #complete(Boolean, Throwable)} once {@link
     *                           #run()} has finalized
     * @param name               the name of the {@link Coordinator} this instruction will be ran in. Used to correctly
     *                           deal with the {@code tokenStore}
     * @param segmentId          the identifier of the {@link Segment} this instruction should merge
     * @param workPackages       the collection of {@link WorkPackage}s controlled by the {@link Coordinator}. Will be
     *                           queried for the presence of the given {@code segmentId} and the segment to merge it
     *                           with
     * @param tokenStore         the storage solution for {@link TrackingToken}s. Used to claim the {@code segmentId} if
     *                           it is not present in the {@code workPackages}, to remove one of the segments and merge
     *                           the merged token
     * @param transactionManager a {@link TransactionManager} used to invoke all {@link TokenStore} operations inside a
     */
    MergeTask(CompletableFuture<Boolean> result,
              String name,
              int segmentId,
              Map<Integer, WorkPackage> workPackages,
              TokenStore tokenStore,
              TransactionManager transactionManager) {
        super(result, name);
        this.name = name;
        this.segmentId = segmentId;
        this.workPackages = workPackages;
        this.transactionManager = transactionManager;
        this.tokenStore = tokenStore;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs a {@link Segment} merge. Will succeed if either the given {@code workPackages} contain the {@link
     * WorkPackage}s corresponding to the given {@code segmentId} and the identifier to merge with. Or, if the {@link
     * TrackingToken}(s) for the segments can be claimed.
     */
    @Override
    protected CompletableFuture<Boolean> task() {
        logger.debug("Processor [{}] will perform merge instruction for segment {}.", name, segmentId);

        int[] segments = transactionManager.fetchInTransaction(() -> tokenStore.fetchSegments(name));
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
                              .thenApply(e -> fetchTokenInTransaction(segmentId))
                : CompletableFuture.completedFuture(fetchTokenInTransaction(segmentId));
    }

    private TrackingToken fetchTokenInTransaction(int segmentId) {
        return transactionManager.fetchInTransaction(() -> tokenStore.fetchToken(name, segmentId));
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

        transactionManager.executeInTransaction(() -> {
            tokenStore.deleteToken(name, tokenToDelete);
            tokenStore.storeToken(mergedToken, name, mergedSegment.getSegmentId());
            tokenStore.releaseClaim(name, mergedSegment.getSegmentId());
        });
        logger.info("Processor [{}] successfully merged {} with {} into {}.",
                    name, thisSegment, thatSegment, mergedSegment);
        return true;
    }

    @Override
    String getDescription() {
        return "Merge Task for segment " + segmentId;
    }
}
