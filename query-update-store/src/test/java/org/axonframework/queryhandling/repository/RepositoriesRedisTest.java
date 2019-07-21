package org.axonframework.queryhandling.repository;

import org.axonframework.queryhandling.EmbeddedRedisTestConfiguration;
import org.axonframework.queryhandling.config.DistributedQueryBusRedisAutoConfiguration;
import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        EmbeddedRedisTestConfiguration.class,
        DistributedQueryBusRedisAutoConfiguration.class,
        RedisAutoConfiguration.class,
        JacksonSerializerTestConfig.class
})
@ActiveProfiles({"spring-test-redis", "spring-test-hsqldb"})
@DirtiesContext
public class RepositoriesRedisTest extends AbstractRepositoriesTest {

}
