package org.axonframework.metrics;

import com.codahale.metrics.MetricSet;

/**
 * Interface indicating that the implementation is capable of reporting state and/or usage statistics using Metrics.
 * The
 * {@link #getMetricSet()} method returns the metrics of the implementation, which can be assembled in a {@link
 * com.codahale.metrics.MetricRegistry}.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public interface MetricSupport {

    /**
     * Returns the MetricSet containing the Metrics that report the state and/or usage statistics for this component.
     * These metrics are registered using an implementation-specific prefix.
     *
     * @return the MetricSet containing the Metrics that report the state and/or usage statistics for this component
     */
    MetricSet getMetricSet();
}
