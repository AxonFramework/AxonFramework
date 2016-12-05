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
 * @author Allard Buijze
 */
public interface TokenStore {

    /**
     * Stores the given {@code token} in the store. The token marks the current position of the process with given
     * {@code processorName} and {@code segment}. The given {@code token} may be {@code null}.
     * <p/>
     * Any claims made by the current process have their timestamp updated.
     *
     * @param token       The token to store for a given process and segment. May be {@code null}.
     * @param processorName The name of the process for which to store the token
     * @param segment     The index of the segment for which to store the token
     * @throws UnableToClaimTokenException when the token being updated has been claimed by another process.
     */
    void storeToken(TrackingToken token, String processorName, int segment) throws UnableToClaimTokenException;

    /**
     * Returns the last stored {@link TrackingToken token} for the given {@code processorName} and {@code segment}.
     * Returns {@code null} if the store holds no token or if the stored token for the given process and segment is
     * {@code null}.
     * <p/>
     * The token will be claimed by the current process (JVM instance), preventing access by other instances. To release
     * the claim, use {@link #releaseClaim(String, int)}
     *
     * @param processorName The process name for which to fetch the token
     * @param segment     The segment index for which to fetch the token
     * @return The last stored TrackingToken or {@code null} if the store holds no token for given process and segment
     * @throws UnableToClaimTokenException if there is a token for given {@code processorName} and {@code segment}, but
     * they are claimed by another process.
     */
    TrackingToken fetchToken(String processorName, int segment) throws UnableToClaimTokenException;

    /**
     * Release a claim of the token for given {@code processorName} and {@code segment}. If no such claim existed,
     * nothing happens.
     * <p>
     * The caller must ensure not to use any streams opened based on the token for which the claim is released.
     *
     * @param processorName The name of the process owning the token (e.g. a TrackingEventProcessor name)
     * @param segment     the segment for which a token was obtained
     */
    void releaseClaim(String processorName, int segment);
}
