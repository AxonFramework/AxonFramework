/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.redis.eventhandling.tokenstore.repository;

import org.axonframework.redis.eventhandling.tokenstore.RedisTokenEntry;

import java.time.Instant;

/**
 * Interface declaring specification for a Redis Token Repository with support for claim expiration.
 *
 * @author Michael Willemse
 */
public interface RedisTokenRepository {

    /**
     * Stores the token entry. Create a new token if it does not exists. Supports pure functional expiration timestamp
     * comparison.
     *
     * @param tokenEntry                The token entry that should be stored
     * @param expirationFromTimestamp   The timestamp from which an existing token entry claim would be regarded as expired.
     * @return                          If the token was successfully stored, return true, otherwise false.
     */
    boolean storeTokenEntry(RedisTokenEntry tokenEntry, Instant expirationFromTimestamp);

    /**
     * Fetches the RedisTokenEntry. If no claim is given to caller, returns null. Supports pure functional expiration
     * timestamp comparison.
     *
     * @param processorName             The name of the process for which to store the token
     * @param segment                   The index of the segment for which to store the token
     * @param owner                     The current nodeId
     * @param timestamp                 The timestamp to be used when storing the token entry
     * @param expirationFromTimestamp   The timestamp from which an existing token entry claim would be regarded as expired.
     * @return                          RedisTokenEntry representation of the Redis stored token entry.
     *                                  If no claim is given to caller, returns null.
     */
    RedisTokenEntry fetchTokenEntry(String processorName, int segment, String owner, Instant timestamp, Instant expirationFromTimestamp);

    /**
     * Releases the claim on the token entry when the node is currently holding the claim
     *
     * @param processorName The name of the process for which to store the token
     * @param segment       The index of the segment for which to store the token
     * @param owner         The current nodeId
     * @return              If the claim was successfully removed, return true, otherwise false.
     */
    boolean releaseClaim(String processorName, int segment, String owner);
}
