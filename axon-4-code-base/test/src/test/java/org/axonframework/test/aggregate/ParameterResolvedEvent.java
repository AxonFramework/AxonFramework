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

package org.axonframework.test.aggregate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Event payload dedicated to triggering the resolution of parameters through a custom {@link
 * org.axonframework.messaging.annotation.ParameterResolverFactory}.
 *
 * @author Steven van Beelen
 */
public class ParameterResolvedEvent {

    private final Object aggregateIdentifier;
    private final AtomicBoolean assertion;

    ParameterResolvedEvent(Object aggregateIdentifier, AtomicBoolean assertion) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.assertion = assertion;
    }

    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public AtomicBoolean getAssertion() {
        return assertion;
    }
}
