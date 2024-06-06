package org.axonframework.eventhandling.pooled;

import java.util.function.Function;

public interface MaxSegmentProvider extends Function<String, Integer> {
    int getMaxSegments(String processingGroup);
    default Integer apply(String processingGroup) {
        return getMaxSegments(processingGroup);
    }
}
