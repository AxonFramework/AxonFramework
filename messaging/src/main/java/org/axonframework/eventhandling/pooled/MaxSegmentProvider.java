package org.axonframework.eventhandling.pooled;

import java.util.function.Function;


/**
 * Defines the maximum number of segments this {@link org.axonframework.eventhandling.StreamingEventProcessor} may claim. Defaults to {@value
 * Short#MAX_VALUE}. This provides the interface to configure a function and the ability to configure the number of segments a single
 * processing instance can claim at the runtime. The {@link org.axonframework.eventhandling.pooled.Coordinator} can
 * rely on this function to query maximum segments at the runtime * {@link org.axonframework.eventhandling.pooled.Coordinator#releaseSegmentsIfTooManyClaimed}
  when more event processing instances are available. This supports a fair distribution of the segments among all the event processing instances
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
