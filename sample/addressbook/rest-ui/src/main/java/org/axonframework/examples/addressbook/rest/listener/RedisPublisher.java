package org.axonframework.examples.addressbook.rest.listener;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.IOException;
import java.io.StringWriter;

/**
 * <p>The redis publisher is used to publish messages to a redis store to be used in a Pub/Sub mechanism. Connections
 * to redis are taken from the provided pool. If the redis store is not available, the publish method should not fail.
 * Therefore we only log that we cannot store the message and immediately return.</p>
 * <p>We use the jedis library which has a strange api, the pool also has a method to return a broken connection. For
 * this example we do not use it. Be sure to come with a better check/solution in your production solution.</p>
 *
 * @author Jettro Coenradie
 */
@Component
public class RedisPublisher implements Publisher {
    private final static Logger logger = LoggerFactory.getLogger(RedisPublisher.class);
    private JedisPool jedisPool;
    private ObjectMapper mapper;

    public RedisPublisher() {
        mapper = new ObjectMapper();
    }

    @Override
    public void publish(Message<?> message) {
        StringWriter writer = new StringWriter();
        try {
            mapper.writeValue(writer, message);
        } catch (IOException e) {
            logger.warn("Problem while writing ContactEntry to a string writer", e);
            return;
        }

        Jedis jedis;
        try {
            jedis = jedisPool.getResource();
        } catch (JedisConnectionException e) {
            logger.debug("Could not obtain redis connection from the pool");
            return;
        }
        try {
            jedis.publish("nl.axonframework.examples.addressbook", writer.toString());
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    @Autowired
    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

}
