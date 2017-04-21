package org.axonframework.eventhandling;


import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * A representation of a segment and corresponding mask with various capabilities.
 * <br/><br/>
 * <i><u>Definition</u></i>
 * <br/><br/>
 * <p>
 * A {@link Segment} is a fraction of the total population of events.
 * The 'mask' is a bitmask to be applied to an identifier, resulting in the segmentId of the {@link Segment}.
 * The following capabilities are supported.
 * <ul>
 * <li>Apply on an identifier, to determine inclusion.</li>
 * <li>Split it into two, with the original segmentId getting the derived mask and a new computed new segmentId</li>
 * <li>Split multiple in a balanced fashion.</li>
 * <li>Merge a pair of segments.</li>
 * <li>Merge multiple to the lowest possible number of segments.</li>
 * <li>Compute segments from a list of segmentId's.</li>
 * </ul>
 */
public class Segment implements Comparable<Segment> {


    public static final int ZERO_MASK = 0x0;

    public static final Segment ROOT_SEGMENT = new Segment(0, ZERO_MASK);

    public static Predicate<Segment[]> MERGEABLE_SEGMENT = segmentPair -> {
        boolean mustBeTwo = segmentPair.length == 2;

        if (!mustBeTwo) return false;

        final Segment segment0 = segmentPair[0];
        final Segment segment1 = segmentPair[1];


        boolean mustHaveSameMask = segment0.getMask() == segment1.getMask();
        boolean mustBeOrdered = segment0.getSegmentId() < segment1.getSegmentId();

        boolean mustBeOddOrEvenWhenNotRoot = true;
        if (!(segment0.getSegmentId() == 0 && segment1.getSegmentId() == 1)) {
            mustBeOddOrEvenWhenNotRoot = segment0.getSegmentId() % 2 == segment1.getSegmentId() % 2;
        }
        return mustHaveSameMask && mustBeOrdered && mustBeOddOrEvenWhenNotRoot;
    };

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

    private static List<Segment[]> analyse(List<Segment> segments) {

        // Sort our segments in natural segmentId order.
        segments = segments.stream().sorted().collect(toList());

        final List<Segment[]> preAnalysis = new ArrayList<>();

        // Special handling for 0,1 segment case. (The First split case), REVIEW: Consider using static Segments for this special case.
        if (segments.size() == 2 && segments.contains(new Segment(0, 0x1)) && segments.contains(new Segment(1, 0x1))) {
            preAnalysis.add(segments.toArray(new Segment[2]));
            return preAnalysis;
        }

        // Double group by odd/even and then the mask, unwrap the pairs into an array.
        segments.stream().
                collect(Collectors.groupingBy(sm -> sm.getSegmentId() % 2, Collectors.groupingBy(Segment::getMask)))
                .values()
                .forEach(
                        grouped -> {
                            grouped.values().forEach(
                                    pairedSegments -> {
                                        preAnalysis.add(pairedSegments.toArray(new Segment[pairedSegments.size()]));
                                    }
                            );
                        }
                );
        // Sort and group by consecutive pairs, when the collection is of un-even size, a pair will have an empty slot.
        final List<Segment[]> finalAnalysis = new ArrayList<>();
        preAnalysis.forEach(s -> {

            final Deque<Segment> segmentGroup = new LinkedList<>();
            segmentGroup.addAll(Arrays.asList(s));

            // track processed...
            List<Segment> track = new ArrayList<>();
            while (!segmentGroup.isEmpty()) {
                List<Segment> pair = new ArrayList<Segment>();

                Segment segment = null;
                do {
                    segment = segmentGroup.pollFirst();
                } while (track.contains(segment));
                if (Objects.isNull(segment)) {
                    break;
                }
                track.add(segment);
                pair.add(segment);
                // find the corresponding segment, if not we have a non-mergeable segment for the current cycle.
                final int pairedSegmentId = segment.getMask() ^ (segment.getMask() >>> 1);
                final Segment pairedSegment = new Segment(segment.getSegmentId() + pairedSegmentId, segment.getMask());
                if (segmentGroup.contains(pairedSegment)) {
                    track.add(pairedSegment);
                    pair.add(pairedSegment);
                }
                finalAnalysis.add(pair.toArray(new Segment[pair.size()]));
            }
        });
        return finalAnalysis;
    }

    /**
     * Compute the {@link Segment}'s from a given list of segmentId's
     * <br/>
     * @param segments The segment id's for which to compute Segments.
     * @return an array of computed {@link Segment}
     */
    public static Segment[] computeSegments(int[] segments) {

        final Set<Segment> resolvedSegments = new HashSet<>();
        computeSegments(ROOT_SEGMENT, stream(segments).boxed().collect(toList()), resolvedSegments);

        // As we split and compute segmentmasks branching by first entry, the resolved segmentmask is not guaranteed to
        // be added to the collection in natural order.
        return resolvedSegments.stream().sorted().collect(toList()).toArray(new Segment[resolvedSegments.size()]);
    }

    public static List<Segment> mergeToMinimum(List<Segment> candidates) {
        final List<Segment> workingList = new ArrayList<>();
        workingList.addAll(candidates);

        boolean moreToMerge = true;
        while (moreToMerge) {

            // Get a collection of segment pairs. A pair containing two segments, is mergeable.
            final List<Segment[]> analyse = analyse(workingList);

            // REVIEW: Could exit, when analyse is 1, and pair is 1 entry.

            // find and remove the mergable entries, the segment ids with an entry in the analysis.
            final List<Segment> mergeables = analyse.stream()
                    .filter(pair -> pair.length == 2)
                    .map(Arrays::asList)
                    .flatMap(Collection::stream)
                    .collect(toList());

            workingList.removeAll(mergeables);

            // Merge the mergeables.
            final List<Segment> merged = analyse.stream()
                    .filter(MERGEABLE_SEGMENT)
                    .map(Segment::merge)
                    .filter(Objects::nonNull)
                    .collect(toList());

            if (merged.isEmpty()) {
                moreToMerge = false;
            } else {
                // Add the non-mergeables.
                workingList.addAll(merged);
            }
        }
        return workingList;
    }

    /**
     * Merge two {@code Segment segements} into one.
     * Conditions apply, see: {@link #analyse}
     * <br/>
     * @param segments The segments to merge.
     * @return the merged segmentId or <code>null</code> if not a {@link Segment#MERGEABLE_SEGMENT}.
     */
    public static Segment merge(Segment[] segments) {
        if (!MERGEABLE_SEGMENT.test(segments)) return null;
        final Segment segment0 = segments[0];
        final Segment segment1 = segments[1];
        return new Segment(segment0.getSegmentId(), segment0.getMask() >>> 1);
    }

    /**
     * Split a given (root) {@link Segment} n-times in round robin fashion.
     * <br/>
     * @param segment       The root {@link Segment} to split.
     * @param numberOfTimes The number of times to split it.
     * @return a collection of {@link Segment}'s.
     */
    public static List<Segment> splitBalanced(Segment segment, int numberOfTimes) {

        final Deque<Segment> toBeSplit = new LinkedList<>();
        toBeSplit.add(segment);

        while (numberOfTimes != 0) {
            final Segment workingSegment = toBeSplit.pollFirst();
            toBeSplit.addAll(Arrays.asList(workingSegment.split()));
            numberOfTimes--;
        }
        return (List<Segment>) toBeSplit;
    }

    /**
     * Groups segments in mergeable pairs. Segments which don't have another segment to merge with, are contained
     * in an array of size 1.
     * <br/>
     * @param segments An array of {@link Segment}'s.
     * @return paired mergeable segments.
     */
    public static List<Segment[]> analyse(Segment[] segments) {
        return analyse(Arrays.asList(segments));
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
     * @return an array of two {@link Segment}'s.
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

    boolean matches(long value) {
        return apply(value) == segmentId;
    }

    private int apply(long value) {
        return Long.valueOf(mask & value).intValue();
    }

    private boolean canSplit(Segment segment) {
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
    public String toString() {
        return String.format("Segment[id,mask]: [ %d, %s]", getSegmentId() , Integer.toBinaryString(getMask()));
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
