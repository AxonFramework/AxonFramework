package org.axonframework.messaging.event.processing;

public interface TrackingToken {

    long position();

    boolean covers(TrackingToken other);

    TrackingToken lowerBound(TrackingToken other);

    TrackingToken upperBound(TrackingToken other);

}
