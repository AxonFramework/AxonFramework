package org.axonframework.queryhandling.benchmark;

import demo.DemoApp;
import org.axonframework.queryhandling.EmbeddedRedisTestConfiguration;
import org.axonframework.queryhandling.config.DistributedQueryBusAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {
        MutedLocalSegmentTestConfig.class,
        EmbeddedRedisTestConfiguration.class,
        DistributedQueryBusAutoConfiguration.class,
        DemoApp.class
})
@ActiveProfiles({"spring-test-redis", "spring-test-hsqldb"})
@DirtiesContext
public class DistributedQueryBusRedisBenchmark extends DistributedQueryBusJpaBenchmark {

}
