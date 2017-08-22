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

package org.axonframework.redis.eventhandling.tokenstore;

import org.axonframework.redis.eventhandling.tokenstore.repository.RedisTokenRepository;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.TemporalAmount;

/**
 * Redis implementation of the TokenStore. With assistance of the DefaultRedisTokenRepository fetch, store and
 * release token functionality is implemented within a single round trip by Redis Lua scripts.
 *
 * @author Michael Willemse
 */
public class RedisTokenStore implements TokenStore {

    private final RedisTokenRepository redisTokenRepository;
    private final Serializer serializer;
    private final TemporalAmount claimTimeout;
    private final String nodeId;
    private final Clock clock = Clock.systemUTC();

    private static final Logger logger = LoggerFactory.getLogger(RedisTokenStore.class);

    /**
     * Initialize the RedisTokenStore with a RedisTokenRepository and a Serializer. The claim timeout configured
     * at 10 seconds and the name of the node is set to a representational name of the Java VM.
     *
     * @param redisTokenRepository  Lower level Redis repository
     * @param serializer            The serializer to serialize tokens with
     */
    public RedisTokenStore(RedisTokenRepository redisTokenRepository, Serializer serializer) {
        this(redisTokenRepository, serializer, Duration.ofSeconds(10), ManagementFactory.getRuntimeMXBean().getName());
    }

    /**
     * Initialize the RedisTokenStore with a RedisTokenRepository, a Serializer. The given {@code claimTimeout} is used to 'steal' any claim
     * that has not been updated since that amount of time.
     *
     * @param redisTokenRepository  Lower level Redis repository
     * @param serializer            The serializer to serialize tokens with
     * @param claimTimeout          The timeout after which this process will force a claim
     * @param nodeId                The identifier to identify ownership of t he tokens
     */
    public RedisTokenStore(RedisTokenRepository redisTokenRepository, Serializer serializer, TemporalAmount claimTimeout, String nodeId) {
        this.redisTokenRepository = redisTokenRepository;
        this.serializer = serializer;
        this.claimTimeout = claimTimeout;
        this.nodeId = nodeId;
    }

    @Override
    public void storeToken(TrackingToken token, String processorName, int segment) throws UnableToClaimTokenException {
        RedisTokenEntry redisTokenEntry = new RedisTokenEntry(token, serializer, processorName, segment);
        redisTokenEntry.claim(nodeId, claimTimeout);
        boolean storedWithClaim = redisTokenRepository.storeTokenEntry(redisTokenEntry, redisTokenEntry.timestamp().minus(claimTimeout));

        if(!storedWithClaim) {
            throw new UnableToClaimTokenException("Unable to claim token.");
        }
    }

    @Override
    public TrackingToken fetchToken(String processorName, int segment) throws UnableToClaimTokenException {
        RedisTokenEntry redisTokenEntry = redisTokenRepository.fetchTokenEntry(processorName, segment, nodeId, clock.instant(), clock.instant().minus(claimTimeout));

        if(redisTokenEntry != null) {
            return redisTokenEntry.getToken(serializer);
        } else {
            throw new UnableToClaimTokenException("Unable to claim token.");
        }
    }

    @Override
    public void releaseClaim(String processorName, int segment) {
        boolean result = redisTokenRepository.releaseClaim(processorName, segment, nodeId);
        if(!result) {
            logger.warn("Releasing claim of token {}/{} failed. It was not owned by {}", processorName, segment, nodeId);
        }
    }
}
