/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.tokenstore;

import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventsourcing.eventstore.TrackingToken;

/**
 * Describes a component capable of storing and retrieving event tracking tokens. An {@link EventProcessor} that is
 * tracking an event stream can use the store to keep track of its position in the event stream. Tokens are stored by
 * process name and segment index, enabling the same processor to be distributed over multiple processes or machines.
 *
 * @author Rene de Waele
 */
public interface TokenStore {

    /**
     * Stores the given {@code token} in the store. The token marks the current position of the process with given
     * {@code processName} and {@code segment}. The given {@code token} may be {@code null}.
     *
     * @param token       The token to store for a given process and segment. May be {@code null}.
     * @param processName The name of the process for which to store the token
     * @param segment     The index of the segment for which to store the token
     */
    void storeToken(TrackingToken token, String processName, int segment);

    /**
     * Returns the last stored {@link TrackingToken token} for the given {@code processName} and {@code segment}.
     * Returns {@code null} if the store holds no token or if the stored token for the given process and segment is
     * {@code null}.
     *
     * @param processName The process name for which to fetch the token
     * @param segment     The segment index for which to fetch the token
     * @return The last stored TrackingToken or {@code null} if the store holds no token for given process and segment
     */
    TrackingToken fetchToken(String processName, int segment);

}
