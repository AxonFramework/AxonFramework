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
import java.util.Collection;

/**
 * Implementation of the {@link org.axonframework.eventhandling.GapAwareTrackingToken} used to bridge serialized
 * versions of this descriptor when migrating from Axon 3.x to Axon 4.x.
 *
 * @author Steven van Beelen
 * @since 4.0.1
 * @deprecated in favor of the {@link org.axonframework.eventhandling.GapAwareTrackingToken}
 */
@Deprecated
public class GapAwareTrackingToken
        extends org.axonframework.eventhandling.GapAwareTrackingToken
        implements Serializable {

    private static final long serialVersionUID = -4691964346972539244L;

    // Fields {@code index} and {@code gaps} are used during Java and XStream de-/serialization through method
    // {@link #readResolve()}
    @SuppressWarnings("unused")
    private long index;
    @SuppressWarnings("unused")
    private Collection<Long> gaps;

    @JsonCreator
    @ConstructorProperties({"index", "gaps"})
    public GapAwareTrackingToken(@JsonProperty("index") long index, @JsonProperty("gaps") Collection<Long> gaps) {
        super(index, createSortedSetOf(gaps, index));
    }

    private Object readResolve() {
        return org.axonframework.eventhandling.GapAwareTrackingToken.newInstance(index, gaps);
    }
}
