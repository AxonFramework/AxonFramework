package org.axonframework.eventsourcing.eventstore;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptySortedSet;
import static junit.framework.TestCase.assertEquals;

public class GapAwareTrackingTokenTest {

    @Test
    public void testAdvanceToWithoutGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(0L, Collections.emptyList());
        subject = subject.advanceTo(1L, 10);
        assertEquals(1L, subject.getIndex());
        assertEquals(emptySortedSet(), subject.getGaps());
    }

    @Test
    public void testAdvanceToWithInitialGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(10L, Arrays.asList(1L, 5L, 6L));
        subject = subject.advanceTo(5L, 10);
        assertEquals(10L, subject.getIndex());
        assertEquals(Stream.of(1L, 6L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test
    public void testAdvanceToWithNewGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(10L, Collections.emptyList());
        subject = subject.advanceTo(13L, 10);
        assertEquals(13L, subject.getIndex());
        assertEquals(Stream.of(11L, 12L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test
    public void testAdvanceToGapClearsOldGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, Arrays.asList(1L, 5L, 12L));
        subject = subject.advanceTo(12L, 10);
        assertEquals(15L, subject.getIndex());
        assertEquals(Stream.of(5L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test
    public void testAdvanceToHigherSequenceClearsOldGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, Arrays.asList(1L, 5L, 12L));
        subject = subject.advanceTo(16L, 10);
        assertEquals(16L, subject.getIndex());
        assertEquals(Stream.of(12L).collect(Collectors.toCollection(TreeSet::new)), subject.getGaps());
    }

    @Test(expected = Exception.class)
    public void testAdvanceToLowerSequenceThatIsNotAGapNotAllowed() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, Arrays.asList(1L, 5L, 12L));
        subject.advanceTo(4L, 10);
    }

    @Test(expected = Exception.class)
    public void testNewInstanceWithGapHigherThanSequenceNotAllowed() {
        GapAwareTrackingToken.newInstance(9L, Arrays.asList(1L, 5L, 12L));
    }
}