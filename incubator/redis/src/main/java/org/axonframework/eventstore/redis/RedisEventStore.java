/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.common.io.IOUtils;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.DomainEventStream;
import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.ConcurrencyException;
import org.axonframework.serializer.Serializer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

/**
 * @author Allard Buijze
 */
public class RedisEventStore implements EventStore {

    private Serializer eventSerializer;
    private RedisConnectionProvider redisConnectionProvider;

    @Override
    public void appendEvents(String type, final DomainEventStream events) {
        Jedis jedis = redisConnectionProvider.newConnection();
        DomainEventMessage firstEvent = events.peek();
        final byte[] key = (type + "." + firstEvent.getAggregateIdentifier()).getBytes(IOUtils.UTF8);
        jedis.watch(key);
        Long eventCount = jedis.llen(key);
        if ((firstEvent.getSequenceNumber() != 0 && eventCount == null)
                || firstEvent.getSequenceNumber() != eventCount) {
            jedis.unwatch();
            throw new ConcurrencyException(
                    String.format("Concurrent modification detected for Aggregate identifier [%s], sequence: [%s]",
                                  firstEvent.getAggregateIdentifier(),
                                  firstEvent.getSequenceNumber()));
        }

        try {
//            jedis.pipelined(new PipelineBlock() {
//                @Override
//                public void execute() {
            Transaction multi = jedis.multi();
            while (events.hasNext()) {
                DomainEventMessage domainEvent = events.next();
                multi.rpush(new String(key, IOUtils.UTF8), new String(eventSerializer.serialize(domainEvent, byte[].class)
                                                                             .getData(), IOUtils.UTF8));
            }
            multi.exec();
//                }
//            });
//            redis.clients.jedis.Transaction transaction = jedis.multi();
//            while (events.hasNext()) {
//                DomainEvent domainEvent = events.next();
//
//                transaction.rpush(new String(key, UTF8), new String(eventSerializer.serialize(domainEvent), UTF8));
//            }
//            transaction.exec();
        } finally {
            redisConnectionProvider.closeConnection(jedis);
        }
    }

    @Override
    public DomainEventStream readEvents(String type, Object identifier) {
        throw new UnsupportedOperationException("Method not yet implemented");
    }

    public void setEventSerializer(Serializer serializer) {
        this.eventSerializer = serializer;
    }

    public void setRedisConnectionProvider(RedisConnectionProvider redisConnectionProvider) {
        this.redisConnectionProvider = redisConnectionProvider;
    }
}
