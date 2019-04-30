package org.axonframework.eventhandling;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.common.Assert;

import java.util.Map;

/**
 * @author Greg Woods
 *
 */
public class MultiSourceTrackingToken implements TrackingToken {

    private final Map<String,TrackingToken> trackingTokens;

    @JsonCreator
    public MultiSourceTrackingToken(@JsonProperty("trackingTokens") Map<String,TrackingToken> trackingTokens){
        this.trackingTokens=trackingTokens;
    }


    @Override
    public TrackingToken lowerBound(TrackingToken other) {
        Assert.isTrue(other instanceof MultiSourceTrackingToken, () -> "Incompatible token type provided.");

        //todo implement
        return null;
    }

    @Override
    public TrackingToken upperBound(TrackingToken other) {
        Assert.isTrue(other instanceof MultiSourceTrackingToken, () -> "Incompatible token type provided.");

        //todo implement
        return null;
    }

    @Override
    public boolean covers(TrackingToken other) {
        Assert.isTrue(other instanceof MultiSourceTrackingToken, () -> "Incompatible token type provided.");

        //todo implement
        return false;
    }

    public MultiSourceTrackingToken advancedTo(String streamName, TrackingToken newTokenForStream){
        trackingTokens.put(streamName,newTokenForStream);
        return new MultiSourceTrackingToken(trackingTokens);
    }


    public TrackingToken getTokenForStream(String streamName){
        return trackingTokens.get(streamName);
    }

    public Map<String, TrackingToken> getTrackingTokens(){
        return trackingTokens;
    }
}
