/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling.processing.streaming.token.store;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jpa.TokenEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for the segment mask migration for token stores with an uninitialized mask field.
 *
 * @author John Hendrikx
 */
@Internal
public class SegmentMaskMigration {

    /**
     * Converts all token entries from a token store to new token entries with the mask field set
     * correctly.
     *
     * @param tokensByProcessorName a map of all tokens to migrate, cannot be {@code null}
     * @param converter a converter, cannot be {@code null}
     * @return a list of migrated token entries, its size being the same as the total number
     *     of entries provided, never {@code null}
     * @throws NullPointerException when any argument is {@code null}
     */
    @Nonnull
    public static List<TokenEntry> migrateMasks(@Nonnull Map<String, List<TokenEntry>> tokensByProcessorName, @Nonnull Converter converter) {
        List<TokenEntry> newEntries = new ArrayList<>();
        int totalSize = 0;

        for (String processorName : tokensByProcessorName.keySet()) {
            List<TokenEntry> entries = tokensByProcessorName.get(processorName);
            int[] segmentIds = entries.stream().map(TokenEntry::getSegment).mapToInt(Segment::getSegmentId).toArray();

            totalSize += entries.size();

            for (Segment fixedSegment : computeSegments(segmentIds)) {
                for (TokenEntry entry : entries) {
                    if (entry.getSegment().getSegmentId() == fixedSegment.getSegmentId()) {
                        newEntries.add(new TokenEntry(processorName, fixedSegment, entry.getToken(converter), converter));
                        break;
                    }
                }
            }
        }

        if (newEntries.size() != totalSize) {
            throw new IllegalStateException("Unexpected amount of new token entries; expected: " + totalSize + ", but was: " + newEntries.size());
        }

        return newEntries;
    }

    private static boolean computeSegments(Segment segment, List<Integer> segmentIds, Set<Segment> applicableSegments) {
        Segment[] splitSegment = segment.split();

        // As the first segmentId mask, keeps the original segmentId, we only check the 2nd segmentId mask being a know.
        if (segmentIds.contains(splitSegment[1].getSegmentId())) {
            for (Segment segmentSplit : splitSegment) {
                if (!computeSegments(segmentSplit, segmentIds, applicableSegments)) {
                    applicableSegments.add(segmentSplit);
                }
            }
        }
        else {
            applicableSegments.add(segment);
        }

        return true;
    }

    /**
     * Compute the {@link Segment}'s from a given list of segmentId's.
     *
     * @param segmentIds The segment id's for which to compute Segments.
     * @return an array of computed {@link Segment}
     */
    private static Segment[] computeSegments(int... segmentIds) {
        if (segmentIds == null || segmentIds.length == 0) {
            throw new IllegalArgumentException("segments cannot be null or empty");
        }

        Set<Segment> resolvedSegments = new HashSet<>();

        computeSegments(Segment.ROOT_SEGMENT, Arrays.stream(segmentIds).boxed().toList(), resolvedSegments);

        // As we split and compute segment masks branching by first entry, the resolved segment mask is not guaranteed
        // to be added to the collection in natural order.
        return resolvedSegments.stream().sorted().toArray(Segment[]::new);
    }
}
