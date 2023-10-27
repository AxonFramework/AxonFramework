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

package org.axonframework.commandhandling.distributed;

import java.util.Optional;

/**
 * Member of a cluster of connected command endpoints.
 *
 * @author Koen Lavooij
 */
public interface Member {

    /**
     * Returns the name of this Member.
     *
     * @return the member name
     */
    String name();

    /**
     * Get the endpoint of this Member given a {@code protocol}. Returns an empty optional if the protocol is not a
     * supported endpoint of this member.
     *
     * @param protocol the expected
     * @param <T> the protocol type
     * @return the endpoint if this member supports the protocol. An empty optional otherwise.
     */
    <T> Optional<T> getConnectionEndpoint(Class<T> protocol);

    /**
     *
     * @return True if the member is local. False if the member is remote or if this information is unknown.
     */
    boolean local();

    /**
     * Mark this member as suspect, i.e. suspected of being (temporarily) unavailable.
     */
    void suspect();

}
