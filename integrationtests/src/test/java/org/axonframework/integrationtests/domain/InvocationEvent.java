/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.domain;

import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class InvocationEvent {

    private final UUID aggregateIdentifier;
    private final int invocationCount;

    public InvocationEvent(UUID aggregateIdentifier, int invocationCount) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.invocationCount = invocationCount;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

    public UUID getAggregateIdentifier() {
        return aggregateIdentifier;
    }
}
