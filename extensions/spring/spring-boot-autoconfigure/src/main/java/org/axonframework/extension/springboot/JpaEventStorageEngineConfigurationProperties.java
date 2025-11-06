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

package org.axonframework.extension.springboot;

import org.axonframework.eventhandling.processors.streaming.token.GapAwareTrackingToken;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateEventEntry;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties describing configuration of Aggregate-based JPA Event Storage engine.
 *
 * @since 5.0.0
 * @author Simon Zambrovski
 */
@ConfigurationProperties("axon.eventstorage.jpa")
public class JpaEventStorageEngineConfigurationProperties {

    private int batchSize = 100;
    private int gapCleaningThreshold = 250;
    private int gapTimeout = 10_000;
    private long lowestGlobalSequence = 1;
    private int maxGapOffset = 60_000;
    private long pollingInterval = 1000;

    /**
     * Batch size to retrieve events from the storage.
     *
     * @return The batch size to retrieve events from the storage.
     */
    public int batchSize() {
        return batchSize;
    }

    /**
     * Retrieves the threshold for number of gaps in a gap-aware token before a cleanup takes place.
     *
     * @return The threshold for number of gaps in a gap-aware token before a cleanup takes place.
     */
    public int gapCleaningThreshold() {
        return gapCleaningThreshold;
    }

    /**
     * Retrieves the time until a gap in global index is considered as timed out.
     *
     * @return The time until a gap in global index is considered as timed out.
     */
    public int gapTimeout() {
        return gapTimeout;
    }

    /**
     * Retrieves the minimum value for the global index auto generation.
     *
     * @return The minimum value for the global index auto generation.
     */
    public long lowestGlobalSequence() {
        return lowestGlobalSequence;
    }

    /**
     * Retrieves the maximum distance in sequence numbers between a gap and the event with the highest known index.
     *
     * @return The maximum distance in sequence numbers between a gap and the event with the highest known index.
     */
    public int maxGapOffset() {
        return maxGapOffset;
    }

    /**
     * Retries the polling interval in milliseconds to detect new appended events.
     *
     * @return The polling interval. Defaults to 1000 ms.
     */
    public long pollingInterval() {
        return pollingInterval;
    }

    /**
     * Sets batch size to retrieve events from the storage.
     *
     * @param batchSize The batch size used to retrieve events from the storage layer. Defaults to {@code 100}.
     */
    public void batchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * Sets the threshold for number of gaps in a gap-aware token before a cleanup takes place.
     *
     * @param gapCleaningThreshold The threshold of the number of gaps in a {@link GapAwareTrackingToken} before an
     *                             attempt to clean them up. Defaults to an integer of {@code 250}.
     */
    public void gapCleaningThreshold(int gapCleaningThreshold) {
        this.gapCleaningThreshold = gapCleaningThreshold;
    }

    /**
     * Sets the time until a gap in global index is considered as timed out.
     *
     * @param gapTimeout The amount of time until a gap in a {@link GapAwareTrackingToken} may be considered timed out
     *                   and thus ready for removal. Defaults to {@code 60000}ms.
     */
    public void gapTimeout(int gapTimeout) {
        this.gapTimeout = gapTimeout;
    }

    /**
     * Sets the minimum value for the global index auto generation.
     *
     * @param lowestGlobalSequence Value the first expected (auto generated)
     *                             {@link AggregateEventEntry#globalIndex() global index} of an
     *                             {@link AggregateEventEntry}. Defaults to {@code 1}.
     */
    public void lowestGlobalSequence(long lowestGlobalSequence) {
        this.lowestGlobalSequence = lowestGlobalSequence;
    }

    /**
     * Sets maximum distance in sequence numbers between a gap and the event with the highest known index.
     *
     * @param maxGapOffset The maximum distance in sequence numbers between a gap and the event with the highest known
     *                     index. Defaults to an integer of {@code 10000}.
     */
    public void maxGapOffset(int maxGapOffset) {
        this.maxGapOffset = maxGapOffset;
    }

    /**
     * Sets the polling interval to detect new appended events.
     *
     * @param pollingInterval The polling interval in milliseconds.
     */
    public void pollingInterval(long pollingInterval) {
        this.pollingInterval = pollingInterval;
    }
}


