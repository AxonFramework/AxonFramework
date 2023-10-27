/*
 * Copyright (c) 2010-2023. Axon Framework
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


import org.axonframework.common.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * A representation of a segment and corresponding mask with various capabilities.
 * <br/><br/>
 * <i><u>Definition</u></i>
 * <br/><br/>
 * <p/>
 * A {@link Segment} is a fraction of the total population of events.
 * The 'mask' is a bitmask to be applied to an identifier, resulting in the segmentId of the {@link Segment}.
 */
public class Segment implements Comparable<Segment> {

    private static final Segment[] EMPTY_SEGMENTS = new Segment[0];
    private static final int ZERO_MASK = 0x0;

    /**
     * Represents the Segment that matches against all input, but can be split to start processing elements in parallel.
     */
    public static final Segment ROOT_SEGMENT = new Segment(0, ZERO_MASK);
    private final int segmentId;
    private final int mask;

    private static boolean computeSegments(Segment segment, List<Integer> segments, Set<Segment> applicableSegments) {

        final Segment[] splitSegment = segment.split();

        // As the first segmentId mask, keeps the original segmentId, we only check the 2nd segmentId mask being a know.
        if (segments.contains(splitSegment[1].getSegmentId())) {
            for (Segment segmentSplit : splitSegment) {
                if (!computeSegments(segmentSplit, segments, applicableSegments)) {
                    applicableSegments.add(segmentSplit);
                }
            }
        } else {
            applicableSegments.add(segment);
        }
        return true;
    }

    /**
     * Compute the {@link Segment}'s from a given list of segmentId's.
     *
     * @param segments The segment id's for which to compute Segments.
     * @return an array of computed {@link Segment}
     */
    public static Segment[] computeSegments(int... segments) {
        if (segments == null || segments.length == 0) {
            return EMPTY_SEGMENTS;
        }
        final Set<Segment> resolvedSegments = new HashSet<>();
        computeSegments(ROOT_SEGMENT, stream(segments).boxed().collect(toList()), resolvedSegments);

        // As we split and compute segment masks branching by first entry, the resolved segment mask is not guaranteed
        // to be added to the collection in natural order.
        return resolvedSegments.stream().sorted().collect(toList()).toArray(new Segment[resolvedSegments.size()]);
    }

    /**
     * Creates a Segment instance for the given {@code segmentId} based on the given {@code availableSegmentsIds}.
     *
     * @param segmentId           The Id of the segment to return
     * @param availableSegmentIds The available segment Ids, to base the mask of the segment on
     * @return the Segment instance representing for the given id
     */
    public static Segment computeSegment(int segmentId, int... availableSegmentIds) {
        Arrays.sort(availableSegmentIds);

        // as a 1 can only happen within the mask, the smallest possible mask is the lowest power of 2 (minus one)
        // higher than that value
        int splitCandidate = segmentId == 0 ? 1 : (Integer.highestOneBit(segmentId) << 1);
        while (Arrays.binarySearch(availableSegmentIds, splitCandidate | segmentId) >= 0) {
            // We have found the split value for the smallest mask. We need to increase the mask
            splitCandidate = splitCandidate << 1;
        }

        int mask = splitCandidate - 1;
        return new Segment(segmentId, mask);
    }

    /**
     * Split a given {@link Segment} n-times in round robin fashion.
     * <br/>
     *
     * @param segment       The {@link Segment} to split.
     * @param numberOfTimes The number of times to split it.
     * @return a collection of {@link Segment}'s.
     */
    public static List<Segment> splitBalanced(Segment segment, int numberOfTimes) {
        final SortedSet<Segment> toBeSplit = new TreeSet<>(Comparator.comparing(Segment::getMask)
                                                                     .thenComparing(Segment::getSegmentId));
        toBeSplit.add(segment);
        for (int i = 0; i < numberOfTimes; i++) {
            final Segment workingSegment = toBeSplit.first();
            toBeSplit.remove(workingSegment);
            toBeSplit.addAll(Arrays.asList(workingSegment.split()));
        }
        ArrayList<Segment> result = new ArrayList<>(toBeSplit);
        result.sort(Comparator.comparing(Segment::getSegmentId));
        return result;
    }

    /**
     * Construct a new Segment instance with given {@code segmentId} and {@code mask}
     *
     * @param segmentId The id of the segment
     * @param mask      The mask of the segment
     */
    protected Segment(int segmentId, int mask) {
        Assert.isTrue(mask == 0 || mask + 1 == Integer.highestOneBit(mask + 1), () -> "Invalid mask. It must end on a consecutive series of 1s");
        this.segmentId = segmentId;
        this.mask = mask;
    }

    /**
     * Calculates the Segment that represents the merger of this segment with the given {@code other} segment.
     *
     * @param other the segment to merge this one with
     * @return The Segment representing the merged segments
     */
    public Segment mergedWith(Segment other) {
        Assert.isTrue(this.isMergeableWith(other), () -> "Given " + other + " cannot be merged with " + this);
        return new Segment(Math.min(this.segmentId, other.segmentId), this.mask >>> 1);
    }

    /**
     * Returns the {@link #getSegmentId() segmentId} of the segment this one can be merged with
     *
     * @return the {@link #getSegmentId() segmentId} of the segment this one can be merged with
     */
    public int mergeableSegmentId() {
        int parentMask = mask >>> 1;
        int firstBit = mask ^ parentMask;

        return segmentId ^ firstBit;
    }

    /**
     * Indicates whether this segment can be merged with the given {@code other} segment.
     * <p>
     * Two segments can be merged when their mask is identical, and the only difference in SegmentID is in the first
     * 1-bit of their mask.
     *
     * @param other the Segment to verify mergeability for
     * @return {@code true} if the segments can be merged, otherwise {@code false}
     */
    public boolean isMergeableWith(Segment other) {
        return this.mask == other.mask
                && mergeableSegmentId() == other.getSegmentId();
    }

    /**
     * Getter for the segment identifier.
     *
     * @return the Segment identifier.
     */
    public int getSegmentId() {
        return segmentId;
    }

    /**
     * Getter for the segment mask.
     *
     * @return the Segment mask.
     */
    public int getMask() {
        return mask;
    }

    /**
     * Returns {@code true} when the mask applied to the given value, matches the segment id.
     *
     * @param value The value to be tested.
     * @return {@code true} when matching this segment.
     */
    public boolean matches(int value) {
        return mask == 0 || (mask & value) == segmentId;
    }

    /**
     * Indicates whether the given {@code value} matches this segment. A value matches when the hashCode of a value,
     * after applying this segments mask, equals to this segment ID.
     *
     * @param value The value to verify against.
     * @return {@code true} if the given value matches this segment, otherwise {@code false}
     */
    public boolean matches(Object value) {
        return mask == 0 || matches(Objects.hashCode(value));
    }

    /**
     * Returns an array with two {@link Segment segments with a corresponding mask}.<br/><br/>
     * The first entry contains the original {@code segmentId}, with the newly calculated mask. (Simple left shift, adding a '1' as LSB).
     * The 2nd entry is a new {@code segmentId} with the same derived mask.
     * <p>
     * Callers must ensure that either the two returned Segments are used, or the instance from which they are derived,
     * but not both.
     *
     * @return an array of two {@link Segment}'s.
     */
    public Segment[] split() {

        if ((mask << 1) < 0) {
            throw new IllegalStateException("Unable to split the given segmentId, as the mask exceeds the max mask size.");
        }

        Segment[] segments = new Segment[2];
        int newMask = ((mask << 1) + 1);
        final int newSegment = segmentId + (mask == 0 ? 1 : newMask ^ mask);
        segments[0] = new Segment(segmentId, newMask);
        segments[1] = new Segment(newSegment, newMask);

        return segments;
    }

    /**
     * Returns the segmentId of the counterpart of this segment, if this segment were to be split.
     *
     * @return the segmentId of the counterpart of this segment
     */
    public int splitSegmentId() {
        return segmentId + (mask == 0 ? 1 : ((mask << 1) + 1) ^ mask);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Segment that = (Segment) o;
        return segmentId == that.segmentId &&
                mask == that.mask;
    }

    @Override
    public String toString() {
        return String.format("Segment[%d/%s]", getSegmentId(), getMask());
    }

    @Override
    public int hashCode() {
        return Objects.hash(segmentId, mask);
    }

    @Override
    public int compareTo(Segment that) {
        return Integer.compare(this.segmentId, that.segmentId);
    }
}
