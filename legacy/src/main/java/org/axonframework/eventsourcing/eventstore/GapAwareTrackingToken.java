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
import java.util.Collection;

/**
 * Class copied from Axon 3 to be able to restore Axon 3 Tokens from Axon 4 applications.
 *
 * @deprecated this class is available for backward compatibility with instances that were serialized with Axon 3. Use
 * {@link org.axonframework.eventhandling.GapAwareTrackingToken} instead.
 */
@Deprecated
public class GapAwareTrackingToken implements Serializable {

    private static final long serialVersionUID = -4691964346972539244L;

    private int index;
    private Collection<Long> gaps;

    /**
     * Get the highest global sequence of events seen up until the point of this tracking token.
     *
     * @return the highest global event sequence number seen so far
     */
    public long getIndex() {
        return index;
    }

    /**
     * Get a {@link Collection} of this token's gaps.
     *
     * @return the gaps of this token
     */
    public Collection<Long> getGaps() {
        return gaps;
    }

    private Object readResolve() {
        return org.axonframework.eventhandling.GapAwareTrackingToken.newInstance(index, gaps);
    }
}
