package org.axonframework.eventhandling.pooled;

import java.util.function.Function;


/**
 * Functional interface returning the maximum amount of segments a {@link Coordinator} may claim, based on the given
 * {@code processingGroup}.
 *
 * @author Manish
 * @since 4.10.0
 */
public interface MaxSegmentProvider extends Function<String, Integer> {

    /**
     * Returns the maximum amount of segments to claim for the given {@code processingGroup}.
     *
     * @param processingGroup The name of a processing group for which to provide the maximum amount of segments it can
     *                        claim.
     * @return The maximum number of segments that can be claimed for the given {@code processingGroup}.
     */
    int getMaxSegments(String processingGroup);

    /**
     * Returns the maximum amount of segments to claim for the given {@code processingGroup}.
     *
     * @param processingGroup The name of a processing group for which to provide the maximum amount of segments it can
     *                        claim.
     * @return The maximum number of segments that can be claimed for the given {@code processingGroup}.
     */
    default Integer apply(String processingGroup) {
        return getMaxSegments(processingGroup);
    }

    /**
     * A {@link MaxSegmentProvider} that always returns {@link Short#MAX_VALUE}.
     *
     * @return A {@link MaxSegmentProvider} that always returns {@link Short#MAX_VALUE}.
     */
    static MaxSegmentProvider maxShort() {
        return processingGroup -> Short.MAX_VALUE;
    }
}
