package org.axonframework.eventhandling;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration object for the {@link TrackingEventProcessor}. The TrackingEventProcessorConfiguration provides access to the options to tweak
 * various settings. Instances are not thread-safe and should not be altered after they have been used to initialize
 * a TrackingEventProcessor.

 * @author Christophe Bouhier
 */
public class TrackingEventProcessorConfiguration {

    private static final int DEFAULT_BATCH_SIZE = 1;
    private static final int DEFAULT_SEGMENTS_SIZE = 1;
    public static final int DEFAULT_POOL_SIZE = 1;

    private int batchSize;
    private int segmentsSize;
    private SequencingPolicy<? super EventMessage<?>> sequentialPolicy;
    private int corePoolSize;
    private int maxPoolSize;
    private ThreadFactory threadFactory;

    public TrackingEventProcessorConfiguration() {
        this.batchSize = DEFAULT_BATCH_SIZE;
        this.segmentsSize = DEFAULT_SEGMENTS_SIZE;
        this.sequentialPolicy = new SequentialPerAggregatePolicy();
        this.corePoolSize = DEFAULT_POOL_SIZE;
        this.maxPoolSize = DEFAULT_POOL_SIZE;
        this.threadFactory = Executors.defaultThreadFactory();
    }

    /**
     * @return the maximum number of events to process in a single batch.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * @param batchSize The maximum number of events to process in a single batch.
     * @return {@code this} for method chaining
     */
    public TrackingEventProcessorConfiguration setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    /**
     * @return the number of segments requested for handling asynchronous processing of events.
     */
    public int getSegmentsSize() {
        return segmentsSize;
    }

    /**
     * @param segmentsSize The number of segments requested for handling asynchronous processing of events.
     * @return {@code this} for method chaining
     */
    public TrackingEventProcessorConfiguration setSegmentsSize(int segmentsSize) {
        this.segmentsSize = segmentsSize;
        return this;
    }

    /**
     * @return the policy which will determine the segmentation identifier.
     */
    public SequencingPolicy<? super EventMessage<?>> getSequentialPolicy() {
        return sequentialPolicy;
    }

    /**
     * @param sequentialPolicy The policy which will determine the segmentation identifier.
     * @return {@code this} for method chaining
     */
    public TrackingEventProcessorConfiguration setSequentialPolicy(SequencingPolicy<? super EventMessage<?>> sequentialPolicy) {
        this.sequentialPolicy = sequentialPolicy;
        return this;
    }


    /**
     * @return the pool size of core threads as per {@link ThreadPoolExecutor#getCorePoolSize()}
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * @param corePoolSize the pool size of core threads as per {@link ThreadPoolExecutor#setCorePoolSize(int)}
     * @return {@code this} for method chaining
     */
    public TrackingEventProcessorConfiguration setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
        return this;
    }

    /**
     * @return the maximum pool size as per {@link ThreadPoolExecutor#getMaximumPoolSize()}
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }


    /**
     * @param maxPoolSize the maximum pool size as per {@link ThreadPoolExecutor#setMaximumPoolSize(int)}
     * @return {@code this} for method chaining
     */
    public TrackingEventProcessorConfiguration setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

  public ThreadFactory getThreadFactory() {
    return threadFactory;
  }

}