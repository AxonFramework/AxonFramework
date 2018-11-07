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

package org.axonframework.eventsourcing.eventstore;

import java.io.Serializable;


/**
 * Class copied from Axon 3 to be able to restore Axon 3 Tokens from Axon 4 applications.
 *
 * @deprecated this class is available for backward compatibility with instances that were serialized with Axon 3. Use
 * {@link org.axonframework.eventhandling.GlobalSequenceTrackingToken} instead.
 */
@Deprecated
public class GlobalSequenceTrackingToken implements Serializable {

    private static final long serialVersionUID = 6161638247685258537L;

    private long globalIndex;

    /**
     * Get the global sequence number of the event
     *
     * @return the global sequence number of the event
     */
    public long getGlobalIndex() {
        return globalIndex;
    }

    private Object readResolve() {
        return new org.axonframework.eventhandling.GlobalSequenceTrackingToken(globalIndex);
    }
}
