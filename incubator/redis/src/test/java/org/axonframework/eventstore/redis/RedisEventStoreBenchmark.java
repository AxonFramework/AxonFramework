/*
 * Copyright (c) 2010-2011. Axon Framework
 *
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

package org.axonframework.eventstore.redis;

import org.apache.commons.pool.impl.GenericObjectPool;
import org.axonframework.integrationtests.eventstore.benchmark.AbstractEventStoreBenchmark;
import org.axonframework.serializer.xml.XStreamSerializer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class RedisEventStoreBenchmark extends AbstractEventStoreBenchmark {

    private RedisEventStore redisEventStore;

    public static void main(String[] args) throws Exception {
        AbstractEventStoreBenchmark benchmark = new RedisEventStoreBenchmark();
        benchmark.startBenchMark();
    }

    @Override
    protected void prepareEventStore() {
        redisEventStore = new RedisEventStore();
        redisEventStore.setEventSerializer(new XStreamSerializer());
        PooledRedisConnectionProvider redisConnectionProvider = new PooledRedisConnectionProvider();
        redisEventStore.setRedisConnectionProvider(redisConnectionProvider);
        Jedis conn = redisConnectionProvider.newConnection();
        conn.flushAll();
        redisConnectionProvider.closeConnection(conn);
    }

    @Override
    protected Runnable getRunnableInstance() {
        return new RedisBenchmark();
    }

    private class RedisBenchmark implements Runnable {

        @Override
        public void run() {
            final UUID aggregateId = UUID.randomUUID();
            int eventSequence = 0;
            for (int t = 0; t < getTransactionCount(); t++) {
                eventSequence = saveAndLoadLargeNumberOfEvents(aggregateId, redisEventStore, eventSequence);
            }
        }
    }

    private class PooledRedisConnectionProvider implements RedisConnectionProvider {

        private JedisPool pool;

        private PooledRedisConnectionProvider() {
            GenericObjectPool.Config poolConfig = new GenericObjectPool.Config();
            poolConfig.maxActive = RedisEventStoreBenchmark.this.getThreadCount();
            poolConfig.minIdle = RedisEventStoreBenchmark.this.getThreadCount();
            pool = new JedisPool(poolConfig, "192.168.56.101", 6379, 10000);
        }

        @Override
        public Jedis newConnection() {
            return pool.getResource();
        }

        public void closeConnection(Jedis toClose) {
            pool.returnResource(toClose);
        }
    }
}
