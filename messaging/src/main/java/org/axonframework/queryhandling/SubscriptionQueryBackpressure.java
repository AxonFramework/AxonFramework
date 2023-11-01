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

package org.axonframework.queryhandling;

import reactor.core.publisher.FluxSink;

/**
 * Backpressure mechanism used for subscription queries. Uses underlying FluxSink.OverflowStrategy from Project Reactor
 * to express the type of back pressure.
 *
 * @author Milan Savic
 * @since 3.3
 * @deprecated since 3.4.0 Reactor version, Sinks API does not use FluxSink.OverflowStrategy
 */
@Deprecated
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
     * Creates default backpressure, using Project Reactor's FluxSink.OverflowStrategy ERROR strategy.
     *
     * @return initialized backpressure, using Project Reactor's FluxSink.OverflowStrategy ERROR strategy
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
