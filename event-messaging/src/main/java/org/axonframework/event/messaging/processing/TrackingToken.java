package org.axonframework.event.messaging.processing;

public interface TrackingToken {

    long position();

    boolean covers(TrackingToken other);

    TrackingToken lowerBound(TrackingToken other);

    TrackingToken upperBound(TrackingToken other);

}
