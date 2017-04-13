package org.axonframework.eventhandling;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

/**
 * @author Christophe Bouhier
 */
public class TrackingEventStrategy {


    public Predicate<Segment[]> MERGEABLE_SEGMENT = segmentPair -> {
        boolean mustBeTwo = segmentPair.length == 2;

        final Segment segment0 = segmentPair[0];
        final Segment segment1 = segmentPair[1];

        boolean mustHaveSameMask = segment0.getMask() == segment1.getMask();
        boolean mustBeOrdered = segment0.getSegmentId() < segment1.getSegmentId();

        boolean mustBeOddOrEvenWhenNotRoot = true;
        if (!(segment0.getSegmentId() == 0 && segment1.getSegmentId() == 1)) {
            mustBeOddOrEvenWhenNotRoot = segment0.getSegmentId() % 2 == segment1.getSegmentId() % 2;
        }
        return mustBeTwo && mustHaveSameMask && mustBeOrdered && mustBeOddOrEvenWhenNotRoot;
    };

    public static long toLong(String uuidAsString) {
        final UUID uuid = UUID.fromString(uuidAsString);
        return uuid.getLeastSignificantBits();
    }


    /**
     * Compute the {@link Segment}'s from a given list of segmentId's
     *
     * @param segments
     * @return
     */
    public static Segment[] computeSegments(int[] segments) {

        final Set<Segment> resolvedMasks = new HashSet<>();
        Segment.computeSegments(Segment.ROOT_SEGMENT, stream(segments).boxed().collect(Collectors.toList()), resolvedMasks);

        // As we split and compute segmentmasks branching by first entry, the resolved segmentmask is not guaranteed to
        // be added to the collection in natural order.
        return resolvedMasks.stream().sorted().collect(Collectors.toList()).toArray(new Segment[resolvedMasks.size()]);
    }


    public static List<Segment> merge(List<Segment> toBeMerged){
        throw new UnsupportedOperationException("TODO, analyse and merge iteration");
        // Recursive
        // Analyse, (will group pairs by odd/even and mask).
        // - Check pairs to be mergeable.
        // - Merge
        // - add result to list of merged items and non-mergable items.
        // - Analyse again.
//        return Collections.emptyList();
    }

    /**
     * Merge two {@code Segment segements} into one.
     * Conditions apply. {@link #analyse}
     *
     * @param segments The segments to merge.
     * @return the merged segmentId.
     */
    public static Segment merge(Segment[] segments) {
        if (segments.length != 2) {
            throw new IllegalArgumentException("We need two segments to merge. ");
        }
        final Segment segment0 = segments[0];
        final Segment segment1 = segments[1];

        if (segment0.getMask() != segment1.getMask()) {
            throw new IllegalArgumentException("Can't merge segments with unmatching masks");
        }
        if (segment0.getSegmentId() >= segment1.getSegmentId()) {
            throw new IllegalArgumentException("Segments should be in natural order");
        }

        if (!(segment0.getSegmentId() == 0 && segment1.getSegmentId() == 1)) {
            if (segment0.getSegmentId() % 2 != segment1.getSegmentId() % 2) {
                throw new IllegalArgumentException("Segments should be both odd or even");
            }
        }
        return new Segment(segment0.getSegmentId(), segment0.getMask() >>> 1);
    }


    public static List<Segment[]> analyse(Segment[] segments) {
        return Segment.analyse(Arrays.asList(segments));
    }

    /**
     * A representation of a segmentId and corresponding mask with various capabilities.
     * <br/><br/>
     * <i><u>Definition</u></i>
     * <br/><br/>
     * <p>
     * A 'segmentId' is a portion of the total population of events.
     * A 'mask' is a bitmask to be applied to an identifier, resulting in the corresponding segmentId.
     * <ul>
     * <li>Apply on an identifier, to determine inclusion.</li>
     * <li>Split it into two, with the original segmentId getting the derived mask.</li>
     * <li>Merge multiple to the lowest possible number of segments</li>
     * </ul>
     */
    public static class Segment implements Comparable<Segment> {


        public static final int ZERO_MASK = 0x0;

        public static final Segment ROOT_SEGMENT = new Segment(0, ZERO_MASK);

        private int segmentId;
        private int mask;

        Segment(int segmentId, int mask) {
            this.segmentId = segmentId;
            // REVIEW: We could validate the mask consisting of 1's only, expect the 0 mask.
            this.mask = mask;
        }

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
         * Analyse a series of @{@code Segment}, and find possible mergeable combinations.
         */
        private static List<Segment[]> analyse(List<Segment> segments) {

            final List<Segment[]> analysedPairs = new ArrayList<>();

            // Double group by odd/even and then the mask, unwrap the pairs into an array.
            segments.stream().
                    collect(Collectors.groupingBy(sm -> sm.getSegmentId() % 2, Collectors.groupingBy(Segment::getMask)))
                    .values()
                    .forEach(
                            grouped -> {
                                grouped.values().forEach(
                                        pairedSegments -> {
                                            analysedPairs.add(pairedSegments.toArray(new Segment[pairedSegments.size()]));
                                        }
                                );
                            }
                    );
            return analysedPairs;
        }

        /**
         * Returns an array with two {@link Segment segments with a corresponding mask}.<br/><br/>
         * The first entry contains the original {@code segmentId}, with the newly calculated mask. (Simple left shift, adding a '1' as LSB).
         * The 2nd entry is a new {@code segmentId} with the same derived mask.
         * <br/><br/>
         * <i><u>Background</u></i>
         * <br/><br/>
         * The algorithm ensures, a given {@code Segment} can be split into two, resulting in a computed new unique segmentId.
         * The new segmentId is computed, with the <code>highest 'one' bit for the original mask, shifted right</code>. In calculus
         * this is the position of the highest 'one' bit, represented as a decimal number, which is square root.
         *
         * @return an array of two {@code Segment}'s.
         */
        Segment[] split() {

            if (!canSplit(this)) {
                throw new IllegalArgumentException("Unable to split the given segmentId, as the mask exceeds the max mask size.");
            }

            Segment[] segments = new Segment[2];
            int newMask = ((mask << 1) + 1);

            final int newSegment = segmentId + (mask == 0 ? 1 : newMask ^ mask);
            segments[0] = new Segment(segmentId, newMask);
            segments[1] = new Segment(newSegment, newMask);

            return segments;
        }

        boolean isMatchingSegment(long value) {
            return apply(value) == segmentId;
        }

        private int apply(long value) {
            return Long.valueOf(mask & value).intValue();
        }

        boolean canSplit(Segment segment) {
            final int shiftedMask = segment.mask << 1;
            return shiftedMask >= 0;
        }

        int getSegmentId() {
            return segmentId;
        }

        int getMask() {
            return mask;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Segment that = (Segment) o;
            return segmentId == that.segmentId &&
                    mask == that.mask;
        }

        @Override
        public int hashCode() {
            return Objects.hash(segmentId, mask);
        }

        @Override
        public int compareTo(Segment that) {
            return this.segmentId - that.segmentId;
        }
    }
}