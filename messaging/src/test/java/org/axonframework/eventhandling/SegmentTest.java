/*
 * Copyright (c) 2010-2024. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.axonframework.messaging.QualifiedNameUtils.dottedName;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class SegmentTest {

    private List<DomainEventMessage<?>> domainEventMessages;

    @BeforeEach
    void before() {
        domainEventMessages = produceEvents();
    }

    @Test
    void segmentSplitAddsUp() {
        final List<Long> identifiers = domainEventMessages.stream().map(de -> {
            final String aggregateIdentifier = de.getAggregateIdentifier();
            return UUID.fromString(aggregateIdentifier).getLeastSignificantBits();
        }).toList();

        // segment 0, mask 0;
        final long count = identifiers.stream().filter(Segment.ROOT_SEGMENT::matches).count();
        assertThat(count, is((long) identifiers.size()));

        final Segment[] splitSegment = Segment.ROOT_SEGMENT.split();

        final long splitCount1 = identifiers.stream().filter(splitSegment[0]::matches).count();
        final long splitCount2 = identifiers.stream().filter(splitSegment[1]::matches).count();

        assertThat(splitCount1 + splitCount2, is((long) identifiers.size()));
    }

    @Test
    void segmentSplit() {

        // Split segment 0
        final Segment[] splitSegment0 = Segment.ROOT_SEGMENT.split();
        assertThat(Segment.ROOT_SEGMENT.splitSegmentId(), is(1));

        assertThat(splitSegment0[0].getSegmentId(), is(0));
        assertThat(splitSegment0[0].getMask(), is(0x01));

        assertThat(splitSegment0[1].getSegmentId(), is(1));
        assertThat(splitSegment0[1].getMask(), is(0x01));

        // Split segment 0 again
        final Segment[] splitSegment0_1 = splitSegment0[0].split();
        assertThat(splitSegment0[0].splitSegmentId(), is(2));

        assertThat(splitSegment0_1[0].getSegmentId(), is(0));
        assertThat(splitSegment0_1[0].getMask(), is(0x3));

        assertThat(splitSegment0_1[1].getSegmentId(), is(2));
        assertThat(splitSegment0_1[1].getMask(), is(0x3));

        // Split segment 0 again
        final Segment[] splitSegment0_2 = splitSegment0_1[0].split();
        assertThat(splitSegment0_1[0].splitSegmentId(), is(4));

        assertThat(splitSegment0_2[0].getSegmentId(), is(0));
        assertThat(splitSegment0_2[0].getMask(), is(0x7));

        assertThat(splitSegment0_2[1].getSegmentId(), is(4));
        assertThat(splitSegment0_2[1].getMask(), is(0x7));


        // Split segment 0 again
        final Segment[] splitSegment0_3 = splitSegment0_2[0].split();
        assertThat(splitSegment0_2[0].splitSegmentId(), is(8));

        assertThat(splitSegment0_3[0].getSegmentId(), is(0));
        assertThat(splitSegment0_3[0].getMask(), is(0xF));

        assertThat(splitSegment0_3[1].getSegmentId(), is(8));
        assertThat(splitSegment0_3[1].getMask(), is(0xF));

        //////////////////////////////////////////////////////////////

        // Split segment 1
        final Segment[] splitSegment1 = splitSegment0[1].split();
        assertThat(splitSegment0[1].splitSegmentId(), is(3));

        assertThat(splitSegment1[0].getSegmentId(), is(1));
        assertThat(splitSegment1[0].getMask(), is(0x3));

        assertThat(splitSegment1[1].getSegmentId(), is(3));
        assertThat(splitSegment1[1].getMask(), is(0x3));

        // Split segment 3
        final Segment[] splitSegment3 = splitSegment1[1].split();
        assertThat(splitSegment1[1].splitSegmentId(), is(7));

        assertThat(splitSegment3[0].getSegmentId(), is(3));
        assertThat(splitSegment3[0].getMask(), is(0x7));

        assertThat(splitSegment3[1].getSegmentId(), is(7));
        assertThat(splitSegment3[1].getMask(), is(0x7));
    }

    @Test
    void segmentSplitNTimes() {
        {
            //
            final List<Segment> segmentMasks = Segment.splitBalanced(Segment.ROOT_SEGMENT, 5);
            assertThat(segmentMasks.size(), is(6));
            assertThat(segmentMasks.get(5), equalTo(new Segment(5, 0x7)));
            assertThat(segmentMasks.get(0), equalTo(new Segment(0, 0x7)));
            assertThat(segmentMasks.get(1), equalTo(new Segment(1, 0x7)));
            assertThat(segmentMasks.get(2), equalTo(new Segment(2, 0x3)));
            assertThat(segmentMasks.get(3), equalTo(new Segment(3, 0x3)));
            assertThat(segmentMasks.get(4), equalTo(new Segment(4, 0x7)));
        }
    }

    @Test
    void splitFromRootSegmentAlwaysYieldsSequentialSegmentIds() {
        for (int i = 0; i < 500; i++) {
            List<Segment> segments = Segment.splitBalanced(Segment.ROOT_SEGMENT, i);
            assertEquals(i + 1, segments.size());
            for (int j = 0; j < i; j++) {
                assertEquals(j, segments.get(j).getSegmentId());
            }
        }
    }

    @Test
    void mergeable() {
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
    void mergeableSegment() {
        Segment[] segments = Segment.ROOT_SEGMENT.split();
        assertEquals(segments[1].getSegmentId(), segments[0].mergeableSegmentId());
        assertEquals(segments[0].getSegmentId(), segments[1].mergeableSegmentId());
        assertEquals(0, Segment.ROOT_SEGMENT.mergeableSegmentId());
    }

    @Test
    void mergeSegments() {
        Segment[] segments = Segment.ROOT_SEGMENT.split();
        Segment[] segments2 = segments[0].split();

        assertEquals(segments[0], segments2[0].mergedWith(segments2[1]));
        assertEquals(segments[0], segments2[1].mergedWith(segments2[0]));
        assertEquals(Segment.ROOT_SEGMENT, segments[1].mergedWith(segments[0]));
        assertEquals(Segment.ROOT_SEGMENT, Segment.ROOT_SEGMENT.mergedWith(Segment.ROOT_SEGMENT));
    }

    @Test
    void segmentResolve() {
        {
            final int[] segments = {};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(0));
        }

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

        {
            //
            final int[] segments = {0, 1, 2, 3, 4, 5};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(6));
            assertThat(segmentMasks[0], equalTo(new Segment(0, 0x7)));
            assertThat(segmentMasks[1], equalTo(new Segment(1, 0x7)));
            assertThat(segmentMasks[2], equalTo(new Segment(2, 0x3)));
            assertThat(segmentMasks[3], equalTo(new Segment(3, 0x3)));
            assertThat(segmentMasks[4], equalTo(new Segment(4, 0x7)));
            assertThat(segmentMasks[5], equalTo(new Segment(5, 0x7)));
        }
    }

    @Test
    void computeSegment() {
        for (int segmentCount = 0; segmentCount < 256; segmentCount++) {
            List<Segment> segments = Segment.splitBalanced(Segment.ROOT_SEGMENT, segmentCount);
            int[] segmentIds = new int[segments.size()];
            for (int i = 0; i < segmentIds.length; i++) {
                segmentIds[i] = segments.get(i).getSegmentId();
            }

            for (Segment segment : segments) {
                assertEquals(segment, Segment.computeSegment(segment.getSegmentId(), segmentIds),
                             "Got wrong segment for " + segmentCount + " number of segments");
            }
        }
    }

    @Test
    void computeSegment_Imbalanced() {
        List<Segment> segments = new ArrayList<>();
        Segment initialSegment = Segment.ROOT_SEGMENT;
        for (int i = 0; i < 8; i++) {
            Segment[] split = initialSegment.split();
            initialSegment = split[0];
            segments.add(split[1]);
        }
        segments.add(initialSegment);

        int[] segmentIds = new int[segments.size()];
        for (int i = 0; i < segmentIds.length; i++) {
            segmentIds[i] = segments.get(i).getSegmentId();
        }

        for (Segment segment : segments) {
            assertEquals(segment, Segment.computeSegment(segment.getSegmentId(), segmentIds));
        }
    }

    @Test
    void segmentSplitBeyondBoundary() {
        final Segment segment = new Segment(0, Integer.MAX_VALUE);
        assertThrows(IllegalStateException.class, segment::split);
    }

    @Test
    void segmentSplitOnBoundary() {

        final Segment segment = new Segment(0, Integer.MAX_VALUE >>> 1);
        final Segment[] splitSegment = segment.split();
        assertThat(splitSegment[0].getSegmentId(), is(0));
        assertThat(splitSegment[0].getMask(), is(Integer.MAX_VALUE));

        assertThat(splitSegment[1].getSegmentId(), is((Integer.MAX_VALUE / 2) + 1));
        assertThat(splitSegment[1].getMask(), is(Integer.MAX_VALUE));
    }

    @Test
    void itemsAssignedToOnlyOneSegment() {
        for (int j = 0; j < 10; j++) {
            List<Segment> segments = Segment.splitBalanced(
                    Segment.ROOT_SEGMENT, ThreadLocalRandom.current().nextInt(50) + 1
            );
            for (int i = 0; i < 100_000; i++) {
                String value = UUID.randomUUID().toString();
                assertEquals(1, segments.stream().filter(s -> s.matches(value)).count());
            }
        }
    }

    private List<DomainEventMessage<?>> produceEvents() {
        final ArrayList<DomainEventMessage<?>> events = new ArrayList<>();
        // Produce a set of
        for (int i = 0; i < 10000; i++) {
            String aggregateIdentifier = UUID.randomUUID().toString();
            final DomainEventMessage<?> domainEventMessage = newStubDomainEvent(aggregateIdentifier);
            events.add(domainEventMessage);
        }
        return events;
    }

    private DomainEventMessage<Object> newStubDomainEvent(Object aggregateIdentifier) {
        return new GenericDomainEventMessage<>(
                "aggregateType", aggregateIdentifier.toString(), 0L, dottedName("test.event"), new Object()
        );
    }
}
