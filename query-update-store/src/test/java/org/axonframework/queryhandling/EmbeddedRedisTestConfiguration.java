package org.axonframework.queryhandling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;

@TestConfiguration
public class EmbeddedRedisTestConfiguration {

    private final RedisServer redisServer;

    public EmbeddedRedisTestConfiguration(@Value("${spring.redis.port}") int redisPort) throws IOException {
        this.redisServer = RedisServer.builder()
                .port(redisPort)
                .setting("maxmemory 1G")
                .setting("save 10 1000")
                .build();
    }

    @PostConstruct
    public void startRedis() {
        this.redisServer.start();
    }

    @PreDestroy
    public void stopRedis() {
        this.redisServer.stop();
    }
}
