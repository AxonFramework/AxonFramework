package org.axonframework.eventhandling;

import org.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class MultiSourceTrackingTokenTest {

    MultiSourceTrackingToken testSubject;

    @Before
    public void setUp(){
        Map<String,TrackingToken> tokenMap = new HashMap<>();
        tokenMap.put("token1", new GlobalSequenceTrackingToken(0));
        tokenMap.put("token2", new GlobalSequenceTrackingToken(0));

        testSubject = new MultiSourceTrackingToken(tokenMap);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIncompatibleToken(){
        testSubject.covers(new GlobalSequenceTrackingToken(0));
    }

    @Test
    public void lowerBound() {
        Map<String,TrackingToken> newTokens =
                new HashMap<String,TrackingToken>(){{put("token1",new GlobalSequenceTrackingToken(1)); put("token2", new GlobalSequenceTrackingToken(2));}};
        Map<String,TrackingToken> expectedTokens =
                new HashMap<String,TrackingToken>(){{put("token1",new GlobalSequenceTrackingToken(0)); put("token2", new GlobalSequenceTrackingToken(0));}};

        MultiSourceTrackingToken newMultiToken = (MultiSourceTrackingToken) testSubject.lowerBound(new MultiSourceTrackingToken(newTokens));

        assertEquals(new MultiSourceTrackingToken(expectedTokens),newMultiToken);
    }

    @Test
    public void upperBound() {
        Map<String,TrackingToken> newTokens =
                new HashMap<String,TrackingToken>(){{put("token1",new GlobalSequenceTrackingToken(1)); put("token2", new GlobalSequenceTrackingToken(2));}};

        MultiSourceTrackingToken newMultiToken = (MultiSourceTrackingToken) testSubject.upperBound(new MultiSourceTrackingToken(newTokens));

        assertEquals(new MultiSourceTrackingToken(newTokens),newMultiToken);
    }

    @Test
    public void covers() {
        Map<String,TrackingToken> newTokens =
                new HashMap<String,TrackingToken>(){{put("token1",new GlobalSequenceTrackingToken(0)); put("token2", new GlobalSequenceTrackingToken(0));}};

        assertTrue(testSubject.covers(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    public void doesNotCover() {
        Map<String,TrackingToken> newTokens =
                new HashMap<String,TrackingToken>(){{put("token1",new GlobalSequenceTrackingToken(1)); put("token2", new GlobalSequenceTrackingToken(0));}};

        assertFalse(testSubject.covers(new MultiSourceTrackingToken(newTokens)));
    }

    @Test
    public void advancedTo() {
        testSubject.advancedTo("token1", new GlobalSequenceTrackingToken(4));

        assertEquals(new GlobalSequenceTrackingToken(4), testSubject.getTokenForStream("token1"));
        //other token remains unchanged
        assertEquals(new GlobalSequenceTrackingToken(0), testSubject.getTokenForStream("token2"));
    }

    @Test
    public void getTokenForStream() {
        assertEquals(new GlobalSequenceTrackingToken(0), testSubject.getTokenForStream("token1"));
    }

    @Test
    public void hasPosition(){
        assertEquals(0L,testSubject.position().getAsLong());
    }

//    @Test
//    public void postitionIsEmpty(){
//        Map<String,TrackingToken> trackingTokenMap = new HashMap<String, TrackingToken>(){{
//            put("tokenA", new MergedTrackingToken(null,null));
//            put("tokenB", new MergedTrackingToken(null,null));
//        }};
//
//        MultiSourceTrackingToken tokenWithoutPosition = new MultiSourceTrackingToken(trackingTokenMap);
//        assertFalse(tokenWithoutPosition.position().isPresent());
//    }
}