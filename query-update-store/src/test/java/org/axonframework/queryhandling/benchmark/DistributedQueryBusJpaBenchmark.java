package org.axonframework.queryhandling.benchmark;

import demo.DemoApp;
import org.axonframework.queryhandling.DistributedQueryBus;
import org.axonframework.queryhandling.config.DistributedQueryBusAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {
        MutedLocalSegmentTestConfig.class,
        DistributedQueryBusAutoConfiguration.class,
        DemoApp.class
})
@ActiveProfiles({"spring-test-hsqldb"})
public class DistributedQueryBusJpaBenchmark extends AbstractQueryBusBenchmark {

    @Autowired
    public void setDistributedQueryBus(DistributedQueryBus queryBus) {
        super.setQueryBus(queryBus);
    }
}
