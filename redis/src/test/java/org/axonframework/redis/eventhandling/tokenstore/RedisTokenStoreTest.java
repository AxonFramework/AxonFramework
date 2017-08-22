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

import org.axonframework.redis.eventhandling.tokenstore.repository.DefaultRedisTokenRepository;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.UnableToClaimTokenException;
import org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;

public class RedisTokenStoreTest {

    @Rule
    public GenericContainer redis = new GenericContainer("redis:4.0.1")
            .withExposedPorts(6379);

    private TokenStore redisTokenStore;
    private TokenStore concurrentRedisTokenStore;

    @Before
    public void setup() {

        JedisPool jedisPool = new JedisPool(redis.getContainerIpAddress(), redis.getMappedPort(6379));
        redisTokenStore = new RedisTokenStore(new DefaultRedisTokenRepository(jedisPool), new XStreamSerializer(), Duration.ofSeconds(5), "node1");

        JedisPool jedisPool2 = new JedisPool(redis.getContainerIpAddress(), redis.getMappedPort(6379));
        concurrentRedisTokenStore = new RedisTokenStore(new DefaultRedisTokenRepository(jedisPool2), new XStreamSerializer(), Duration.ofSeconds(5), "node2");
    }

    @Test
    public void testFetch() {
        TrackingToken trackingToken = redisTokenStore.fetchToken("processor1", 0);

        assertThat(trackingToken).isNull();
    }

    @Test
    public void testConcurrentFetch() {
        redisTokenStore.fetchToken("processor1", 0);

        assertThatThrownBy(() -> concurrentRedisTokenStore.fetchToken("processor1", 0)).isInstanceOf(UnableToClaimTokenException.class);
    }

    @Test
    public void testConcurrentFetchAfterTimeout() {
        redisTokenStore.fetchToken("processor1", 0);
        try {
            Thread.sleep(6000);
            concurrentRedisTokenStore.fetchToken("processor1", 0);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testReleaseClaim() {
        redisTokenStore.fetchToken("processor1", 0);
        redisTokenStore.releaseClaim("processor1", 0);
    }

    @Test
    //check log message for failed release, no exception should be thrown
    public void testConcurrentReleaseClaim() {
        redisTokenStore.fetchToken("processor1", 0);
        concurrentRedisTokenStore.releaseClaim("processor1", 0);
    }

    @Test
    public void testClaimStoreToken() {
        redisTokenStore.fetchToken("processor1", 0);
        redisTokenStore.storeToken(GapAwareTrackingToken.newInstance(1337L, Collections.emptySortedSet()), "processor1", 0);
        TrackingToken trackingToken = redisTokenStore.fetchToken("processor1", 0);

        assertThat(trackingToken).isInstanceOf(GapAwareTrackingToken.class);
        assertThat(((GapAwareTrackingToken) trackingToken).getIndex()).isEqualTo(1337L);
    }

    @Test
    public void testClaimConcurrentFetchToken() {
        redisTokenStore.fetchToken("processor1", 0);
        redisTokenStore.storeToken(GapAwareTrackingToken.newInstance(1337L, Collections.emptySortedSet()), "processor1", 0);

        assertThatThrownBy(() -> concurrentRedisTokenStore.fetchToken("processor1", 0)).isInstanceOf(UnableToClaimTokenException.class);
    }

    @Test
    public void testClaimConcurrentStoreToken() {
        redisTokenStore.fetchToken("processor1", 0);
        redisTokenStore.storeToken(GapAwareTrackingToken.newInstance(1337L, Collections.emptySortedSet()), "processor1", 0);

        assertThatThrownBy(() -> concurrentRedisTokenStore.storeToken(GapAwareTrackingToken.newInstance(7331L, Collections.emptySortedSet()), "processor1", 0)).isInstanceOf(UnableToClaimTokenException.class);
    }

    @Test
    public void testClaimConcurrentStoreTokenAfterTimeout() {
        redisTokenStore.fetchToken("processor1", 0);
        redisTokenStore.storeToken(GapAwareTrackingToken.newInstance(1337L, Collections.emptySortedSet()), "processor1", 0);
        try {
            Thread.sleep(6000);
            TrackingToken trackingToken = concurrentRedisTokenStore.fetchToken("processor1", 0);

            assertThat(trackingToken).isInstanceOf(GapAwareTrackingToken.class);
            assertThat(((GapAwareTrackingToken) trackingToken).getIndex()).isEqualTo(1337L);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testClaimStoreReleaseFetch() {
        redisTokenStore.fetchToken("processor1", 0);
        redisTokenStore.storeToken(GapAwareTrackingToken.newInstance(1337L, Collections.emptySortedSet()), "processor1", 0);
        redisTokenStore.releaseClaim("processor1", 0);
        TrackingToken trackingToken = concurrentRedisTokenStore.fetchToken("processor1", 0);

        assertThat(trackingToken).isInstanceOf(GapAwareTrackingToken.class);
        assertThat(((GapAwareTrackingToken) trackingToken).getIndex()).isEqualTo(1337L);
    }

    @Test
    public void testMultipleSegments() {
        redisTokenStore.fetchToken("processor1", 0);
        redisTokenStore.storeToken(GapAwareTrackingToken.newInstance(1337L, Collections.emptySortedSet()), "processor1", 0);
        concurrentRedisTokenStore.fetchToken("processor1", 1);
        concurrentRedisTokenStore.storeToken(GapAwareTrackingToken.newInstance(7331L, Collections.emptySortedSet()), "processor1", 1);

        TrackingToken trackingToken = redisTokenStore.fetchToken("processor1", 0);

        assertThat(trackingToken).isInstanceOf(GapAwareTrackingToken.class);
        assertThat(((GapAwareTrackingToken) trackingToken).getIndex()).isEqualTo(1337L);

        TrackingToken trackingToken2 = concurrentRedisTokenStore.fetchToken("processor1", 1);

        assertThat(trackingToken2).isInstanceOf(GapAwareTrackingToken.class);
        assertThat(((GapAwareTrackingToken) trackingToken2).getIndex()).isEqualTo(7331L);
    }

    @Test
    public void testMultipleProcessors() {
        redisTokenStore.fetchToken("processor1", 0);
        redisTokenStore.storeToken(GapAwareTrackingToken.newInstance(1337L, Collections.emptySortedSet()), "processor1", 0);
        concurrentRedisTokenStore.fetchToken("processor2", 0);
        concurrentRedisTokenStore.storeToken(GapAwareTrackingToken.newInstance(7331L, Collections.emptySortedSet()), "processor2", 0);

        TrackingToken trackingToken = redisTokenStore.fetchToken("processor1", 0);

        assertThat(trackingToken).isInstanceOf(GapAwareTrackingToken.class);
        assertThat(((GapAwareTrackingToken) trackingToken).getIndex()).isEqualTo(1337L);

        TrackingToken trackingToken2 = concurrentRedisTokenStore.fetchToken("processor2", 0);

        assertThat(trackingToken2).isInstanceOf(GapAwareTrackingToken.class);
        assertThat(((GapAwareTrackingToken) trackingToken2).getIndex()).isEqualTo(7331L);
    }
}