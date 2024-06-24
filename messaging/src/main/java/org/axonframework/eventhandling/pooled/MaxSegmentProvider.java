package org.axonframework.eventhandling.pooled;

import java.util.function.Function;


/**
 * Defines the maximum number of segment this {@link org.axonframework.eventhandling.StreamingEventProcessor} may claim. Defaults to {@value
 * Short#MAX_VALUE}. This provides interface to configure a function which provides ability to configure the number of segments a single
 * processing instance can claim at the runtime. This provides {@link org.axonframework.eventhandling.pooled.Coordinator} ability to
 * continuously rely on this function to query maximum segments at the runtime so that extra segments can be released
 * {@link org.axonframework.eventhandling.pooled.Coordinator#releaseSegmentsIfTooManyClaimed} when more event processing instances are available at the runtime.
 *
 * @param processingGroup the name of the event processor
 *
 * @return the maximum number of segments that can be claimed by an instance.
 */
public interface MaxSegmentProvider extends Function<String, Integer> {
    int getMaxSegments(String processingGroup);
    default Integer apply(String processingGroup) {
        return getMaxSegments(processingGroup);
    }
}
