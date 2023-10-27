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

package org.axonframework.eventsourcing.eventstore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.beans.ConstructorProperties;
import java.io.Serializable;


/**
 * Implementation of the {@link org.axonframework.eventhandling.GlobalSequenceTrackingToken} used to bridge serialized
 * versions of this descriptor when migrating from Axon 3.x to Axon 4.x.
 *
 * @author Steven van Beelen
 * @since 4.0.1
 * @deprecated in favor of the {@link org.axonframework.eventhandling.GlobalSequenceTrackingToken}
 */
@Deprecated
public class GlobalSequenceTrackingToken
        extends org.axonframework.eventhandling.GlobalSequenceTrackingToken
        implements Serializable {

    private static final long serialVersionUID = 6161638247685258537L;

    // Field {@code globalIndex} is used during Java and XStream de-/serialization through method {@link #readResolve()}
    @SuppressWarnings("unused")
    private long globalIndex;

    /**
     * Initializes a {@link GlobalSequenceTrackingToken} from the given {@code globalIndex} of the event.
     *
     * @param globalIndex the global sequence number of the event
     */
    @JsonCreator
    @ConstructorProperties({"globalIndex"})
    public GlobalSequenceTrackingToken(@JsonProperty("globalIndex") long globalIndex) {
        super(globalIndex);
    }

    private Object readResolve() {
        return new org.axonframework.eventhandling.GlobalSequenceTrackingToken(globalIndex);
    }
}
