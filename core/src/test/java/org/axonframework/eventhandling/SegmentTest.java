/*
 * Copyright (c) 2010-2017. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class SegmentTest {

    private static final Logger LOG = LoggerFactory.getLogger(SegmentTest.class);

    private List<DomainEventMessage> domainEventMessages;

    @Before
    public void before() {
        domainEventMessages = produceEvents();
    }

    @Test
    public void testSegmentSplitAddsUp() {

        final List<Long> identifiers = domainEventMessages.stream().map(de -> {
            final String aggregateIdentifier = de.getAggregateIdentifier();
            return UUID.fromString(aggregateIdentifier).getLeastSignificantBits();
        }).collect(Collectors.toList());

        // segment 0, mask 0;
        final long count = identifiers.stream().filter(Segment.ROOT_SEGMENT::matches).count();
        assertThat(count, is((long)identifiers.size()));

        final Segment[] splitSegment = Segment.ROOT_SEGMENT.split();

        final long splitCount1 = identifiers.stream().filter(splitSegment[0]::matches).count();
        final long splitCount2 = identifiers.stream().filter(splitSegment[1]::matches).count();

        assertThat(splitCount1 + splitCount2, is((long) identifiers.size()));
    }

    @Test
    public void testSegmentSplit() {

        // Split segment 0
        final Segment[] splitSegment0 = Segment.ROOT_SEGMENT.split();

        assertThat(splitSegment0[0].getSegmentId(), is(0));
        assertThat(splitSegment0[0].getMask(), is(0x01));

        assertThat(splitSegment0[1].getSegmentId(), is(1));
        assertThat(splitSegment0[1].getMask(), is(0x01));

        // Split segment 0 again
        final Segment[] splitSegment0_1 = splitSegment0[0].split();

        assertThat(splitSegment0_1[0].getSegmentId(), is(0));
        assertThat(splitSegment0_1[0].getMask(), is(0x3));

        assertThat(splitSegment0_1[1].getSegmentId(), is(2));
        assertThat(splitSegment0_1[1].getMask(), is(0x3));

        // Split segment 0 again
        final Segment[] splitSegment0_2 = splitSegment0_1[0].split();

        assertThat(splitSegment0_2[0].getSegmentId(), is(0));
        assertThat(splitSegment0_2[0].getMask(), is(0x7));

        assertThat(splitSegment0_2[1].getSegmentId(), is(4));
        assertThat(splitSegment0_2[1].getMask(), is(0x7));


        // Split segment 0 again
        final Segment[] splitSegment0_3 = splitSegment0_2[0].split();

        assertThat(splitSegment0_3[0].getSegmentId(), is(0));
        assertThat(splitSegment0_3[0].getMask(), is(0xF));

        assertThat(splitSegment0_3[1].getSegmentId(), is(8));
        assertThat(splitSegment0_3[1].getMask(), is(0xF));

        //////////////////////////////////////////////////////////////

        // Split segment 1
        final Segment[] splitSegment1 = splitSegment0[1].split();

        assertThat(splitSegment1[0].getSegmentId(), is(1));
        assertThat(splitSegment1[0].getMask(), is(0x3));

        assertThat(splitSegment1[1].getSegmentId(), is(3));
        assertThat(splitSegment1[1].getMask(), is(0x3));

        // Split segment 3
        final Segment[] splitSegment3 = splitSegment1[1].split();

        assertThat(splitSegment3[0].getSegmentId(), is(3));
        assertThat(splitSegment3[0].getMask(), is(0x7));

        assertThat(splitSegment3[1].getSegmentId(), is(7));
        assertThat(splitSegment3[1].getMask(), is(0x7));
    }

    @Test
    public void testSegmentSplitNTimes() {

        final List<Segment> splits = Segment.splitBalanced(Segment.ROOT_SEGMENT, 10);

        assertThat(splits.size(), is(11));
    }

    @Test
    public void testMergeable() {
        Segment[] segments = Segment.ROOT_SEGMENT.split();
        assertFalse(segments[0].isMergeableWith(segments[0]));
        assertFalse(segments[1].isMergeableWith(segments[1]));

        assertTrue(segments[0].isMergeableWith(segments[1]));
        assertTrue(segments[1].isMergeableWith(segments[0]));

        // these masks differ
        Segment[] segments2 = segments[0].split();
        assertFalse(segments[0].isMergeableWith(segments2[0]));
        assertFalse(segments[0].isMergeableWith(segments2[1]));
        assertFalse(segments[1].isMergeableWith(segments2[0]));

        // this should still work
        assertTrue(segments2[0].isMergeableWith(segments2[1]));
    }

    @Test
    public void testMergeableSegment() {
        Segment[] segments = Segment.ROOT_SEGMENT.split();
        assertEquals(segments[1].getSegmentId(), segments[0].mergeableSegmentId());
        assertEquals(segments[0].getSegmentId(), segments[1].mergeableSegmentId());
        assertEquals(0, Segment.ROOT_SEGMENT.mergeableSegmentId());
    }

    @Test
    public void testMergeSegments() {
        Segment[] segments = Segment.ROOT_SEGMENT.split();
        Segment[] segments2 = segments[0].split();

        assertEquals(segments[0], segments2[0].mergedWith(segments2[1]));
        assertEquals(segments[0], segments2[1].mergedWith(segments2[0]));
        assertEquals(Segment.ROOT_SEGMENT, segments[1].mergedWith(segments[0]));
        assertEquals(Segment.ROOT_SEGMENT, Segment.ROOT_SEGMENT.mergedWith(Segment.ROOT_SEGMENT));
    }

    @Test
    public void testSegmentResolve() {
        {
            final int[] segments = {0};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(1));
            assertThat(segmentMasks[0].getMask(), is(Segment.ROOT_SEGMENT.getMask()));
        }

        {
            // balanced distribution
            final int[] segments = {0, 1};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(2));
            assertThat(segmentMasks[0].getMask(), is(0x1));
            assertThat(segmentMasks[1].getMask(), is(0x1));
        }

        {
            // un-balanced distribution segment 0 is split.
            final int[] segments = {0, 1, 2};
            final Segment[] segmentMasks = Segment.computeSegments(segments);

            assertThat(segmentMasks.length, is(3));
            assertThat(segmentMasks[0].getMask(), is(0x3));
            assertThat(segmentMasks[1].getMask(), is(0x1));
            assertThat(segmentMasks[2].getMask(), is(0x3));
        }

        {
            // un-balanced distribution segment 1 is split.
            final int[] segments = {0, 1, 3};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(3));
            assertThat(segmentMasks[0].getMask(), is(0x1));
            assertThat(segmentMasks[1].getMask(), is(0x3));
            assertThat(segmentMasks[2].getMask(), is(0x3));

        }

        {
            // balanced distribution segment 0 and 1 are split.
            final int[] segments = {0, 1, 2, 3};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(4));
            assertThat(segmentMasks[0].getMask(), is(0x3));
            assertThat(segmentMasks[1].getMask(), is(0x3));
            assertThat(segmentMasks[2].getMask(), is(0x3));
            assertThat(segmentMasks[3].getMask(), is(0x3));
        }

        {
            // un-balanced distribution segment 1 is split and segment 3 is split.
            final int[] segments = {0, 1, 3, 7};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(4));
            assertThat(segmentMasks[0].getMask(), is(0x1));
            assertThat(segmentMasks[1].getMask(), is(0x3));
            assertThat(segmentMasks[2].getMask(), is(0x7));
            assertThat(segmentMasks[3].getMask(), is(0x7));
        }

        {
            // un-balanced distribution segment 0 is split, segment 3 is split.
            final int[] segments = {0, 1, 2, 3, 7};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(5));
            assertThat(segmentMasks[0].getMask(), is(0x3));
            assertThat(segmentMasks[1].getMask(), is(0x3));
            assertThat(segmentMasks[2].getMask(), is(0x3));
            assertThat(segmentMasks[3].getMask(), is(0x7));
            assertThat(segmentMasks[4].getMask(), is(0x7));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSegmentSplitBeyondBoundary() {
        final Segment segment = new Segment(0, Integer.MAX_VALUE);
        segment.split();
    }

    @Test()
    public void testSegmentSplitOnBoundary() {

        final Segment segment = new Segment(0, Integer.MAX_VALUE >>> 1);
        final Segment[] splitSegment = segment.split();
        assertThat(splitSegment[0].getSegmentId(), is(0));
        assertThat(splitSegment[0].getMask(), is(Integer.MAX_VALUE));

        assertThat(splitSegment[1].getSegmentId(), is((Integer.MAX_VALUE / 2) + 1));
        assertThat(splitSegment[1].getMask(), is(Integer.MAX_VALUE));
    }


    private List<DomainEventMessage> produceEvents() {
        final ArrayList<DomainEventMessage> events = new ArrayList<>();
        // Produce a set of
        for (int i = 0; i < 10000; i++) {
            String aggregateIdentifier = UUID.randomUUID().toString();
            final DomainEventMessage domainEventMessage = newStubDomainEvent(aggregateIdentifier);
            events.add(domainEventMessage);
        }
        return events;
    }

    private DomainEventMessage newStubDomainEvent(String aggregateIdentifier) {
        return new GenericDomainEventMessage<>("type", aggregateIdentifier, (long) 0,
                new Object(), MetaData.emptyInstance());
    }
}
