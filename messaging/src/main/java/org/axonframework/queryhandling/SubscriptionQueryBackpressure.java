/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling;

import reactor.core.publisher.FluxSink;

/**
 * Backpressure mechanism used for subscription queries. Uses underlying {@link FluxSink.OverflowStrategy} to express
 * the type of backpressure.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class SubscriptionQueryBackpressure {

    private final FluxSink.OverflowStrategy overflowStrategy;

    /**
     * Initializes backpressure with reactor's overflow strategy.
     *
     * @param overflowStrategy For backpressure handling
     */
    public SubscriptionQueryBackpressure(FluxSink.OverflowStrategy overflowStrategy) {
        this.overflowStrategy = overflowStrategy;
    }

    /**
     * Creates default backpressure - {@link FluxSink.OverflowStrategy#ERROR}.
     *
     * @return initialized backpressure - {@link FluxSink.OverflowStrategy#ERROR}
     */
    public static SubscriptionQueryBackpressure defaultBackpressure() {
        return new SubscriptionQueryBackpressure(FluxSink.OverflowStrategy.ERROR);
    }

    /**
     * Gets the overflow strategy.
     *
     * @return the overflow strategy
     */
    public FluxSink.OverflowStrategy getOverflowStrategy() {
        return overflowStrategy;
    }
}
