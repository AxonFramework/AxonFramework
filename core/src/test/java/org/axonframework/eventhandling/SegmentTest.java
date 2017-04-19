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
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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
        assertThat(count, is(Long.valueOf(identifiers.size())));

        final Segment[] splitSegment = Segment.ROOT_SEGMENT.split();

        final long splitCount1 = identifiers.stream().filter(splitSegment[0]::matches).count();
        final long splitCount2 = identifiers.stream().filter(splitSegment[1]::matches).count();

        assertThat(splitCount1 + splitCount2, is(Long.valueOf(identifiers.size())));
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
    public void testSegmentSplitNTimesAndMergeBackToOriginal() {

        final List<Segment> splits = Segment.splitBalanced(Segment.ROOT_SEGMENT, 10);

        assertThat(splits.size(), is(11));
        splits.forEach(s -> LOG.info(s.toString()));

        final List<Segment> merged = Segment.mergeToMinimum(splits);

        assertThat(merged.size(), is(1));
        merged.forEach(s -> LOG.info(s.toString()));

    }

    @Test
    public void testSegmentResolve() {
        {
            final int[] segments = {0};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks.length, is(1));
            assertThat(segmentMasks[0].getMask(), is(Segment.ZERO_MASK));
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

    @Test
    public void testSegmentAnalyseAndMergePair() {

        {
            final Segment[] segments = new Segment[]{
                    Segment.ROOT_SEGMENT,
            };
            final List<Segment[]> analyse = Segment.analyse(segments);
            assertThat(analyse.size(), is(1));
            final Segment[] segmentMasks1 = analyse.get(0);
            assertThat(segmentMasks1.length, is(1));
            assertThat(segmentMasks1[0], is(segments[0]));
        }

        {
            // Special case... when segment 0 and 1 are analyzed.
            final Segment[] segments = new Segment[]{
                    new Segment(0, 0x1),
                    new Segment(1, 0x1),
            };

            final List<Segment[]> analyse = Segment.analyse(segments);
            assertThat(analyse.size(), is(1));
            final Segment[] segmentMasks1 = analyse.get(0);
            assertThat(segmentMasks1.length, is(2));
            assertThat(segmentMasks1[0], is(segments[0]));
            assertThat(segmentMasks1[1], is(segments[1]));
        }

        {
            final Segment[] segments = new Segment[]{
                    new Segment(0, 0x3),
                    new Segment(1, 0x1),
                    new Segment(2, 0x3),
            };

            final List<Segment[]> analyse = Segment.analyse(segments);
            assertThat(analyse.size(), is(2));
            final Segment[] segmentMasks1 = analyse.get(0);
            assertThat(segmentMasks1.length, is(2));
            assertThat(segmentMasks1[0], is(segments[0]));
            assertThat(segmentMasks1[1], is(segments[2]));
        }

        {
            final Segment[] segments = new Segment[]{
                    new Segment(0, 0x3),
                    new Segment(1, 0x3),
                    new Segment(2, 0x3),
                    new Segment(3, 0x3)
            };
            final List<Segment[]> analyse = Segment.analyse(segments);
            assertThat(analyse.size(), is(2));
        }

        {
            final Segment[] segments = new Segment[]{
                    new Segment(0, 0x1),
                    new Segment(1, 0x3),
                    new Segment(3, 0x7),
                    new Segment(7, 0x7)
            };
            final List<Segment[]> analyse = Segment.analyse(segments);
            assertThat(analyse.size(), is(3));

            final Segment[] segments4 = analyse.get(2);
            assertThat(segments4.length, is(2));
            assertThat(segments4[0], is(segments[2]));
            assertThat(segments4[1], is(segments[3]));

            // Merge
            final Segment merge = Segment.merge(segments4);
            assertThat(merge.getSegmentId(), is(3));
            assertThat(merge.getMask(), is(0x3));

            // Analyse the original set, with the merged pair again.
            analyse.remove(segments4);

            // Flatten the analysed result, to run through analysis again.
            final List<Segment> collect = analyse.stream().flatMap(Stream::of).collect(Collectors.toList());
            collect.add(merge);
            final Segment[] segments1 = collect.toArray(new Segment[collect.size()]);
            final List<Segment[]> analyse1 = Segment.analyse(segments1);

            // We should now be reduced to 3 (partially filled) pairs of segments.
            assertThat(analyse1.size(), is(2));
        }
    }


    @Test
    public void testSegmentMergeInvalidPair() {

        {
            // different masks..
            final Segment[] segment = new Segment[]{
                    new Segment(0, 0x1),
                    new Segment(1, 0x3),
            };
            final Segment merge = Segment.merge(segment);
            assertThat(merge, CoreMatchers.nullValue());
        }

        {
            // same masks, non matching odd/even.
            final Segment[] segment = new Segment[]{
                    new Segment(0, 0x3),
                    new Segment(3, 0x3),
            };

            final Segment merge = Segment.merge(segment);
            assertThat(merge, CoreMatchers.nullValue());
        }

        {
            // wrong array size.
            final Segment[] segment = new Segment[]{
                    new Segment(0, 0x1),
                    new Segment(1, 0x3),
                    new Segment(3, 0x3),
            };

            final Segment merge = Segment.merge(segment);
            assertThat(merge, CoreMatchers.nullValue());
        }
        {
            // wrong order
            final Segment[] segment = new Segment[]{
                    new Segment(1, 0x1),
                    new Segment(0, 0x1),
            };

            final Segment merge = Segment.merge(segment);
            assertThat(merge, CoreMatchers.nullValue());
        }

    }


    @Test
    public void testSegmentMergeAll() {

        {
            final List<Segment> merge = Segment.mergeToMinimum(asList(Segment.ROOT_SEGMENT));
            assertThat(merge.size(), is(1));
        }
        {
            List<Segment> segments = new ArrayList<Segment>() {
                {
                    add(new Segment(0, 0x1));
                    add(new Segment(1, 0x3));
                    add(new Segment(3, 0x3));
                }
            };

            final List<Segment> merge = Segment.mergeToMinimum(segments);
            assertThat(merge.size(), is(1));
        }

        // Now go wild:
        final List<Segment> segments = Segment.splitBalanced(Segment.ROOT_SEGMENT, 20);
        LOG.info("Split ROOT segment into: {}", segments.size());
        final List<Segment> mergeAll = Segment.mergeToMinimum(segments);
        assertThat(mergeAll.size(), is(1));
        assertThat(mergeAll.get(0), is(Segment.ROOT_SEGMENT));

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