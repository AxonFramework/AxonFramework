package org.axonframework.queryhandling.benchmark;

import demo.DemoApp;
import demo.DemoQuery;
import demo.DemoQueryResult;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.DefaultQueryGateway;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.axonframework.queryhandling.config.DistributedQueryBusAutoConfiguration;
import org.junit.runner.RunWith;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;
import java.util.UUID;

/**
 * https://gist.github.com/msievers/ce80d343fc15c44bea6cbb741dde7e45
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        DistributedQueryBusAutoConfiguration.class,
        DemoApp.class
})
@State(Scope.Benchmark)
public abstract class AbstractQueryBusBenchmark extends AbstractBenchmarkTest {

    private static QueryBus queryBus;

    private static QueryGateway queryGateway;

    protected void setQueryBus(QueryBus queryBus) {
        AbstractQueryBusBenchmark.queryBus = queryBus;
        AbstractQueryBusBenchmark.queryGateway = DefaultQueryGateway.builder()
                .queryBus(queryBus)
                .build();
    }

    @Benchmark
    public void closeSubscription() {
        benchmarkQueryBus(true);
    }

    @Benchmark
    public void leaveSubscription() {
        benchmarkQueryBus(false);
    }

    private void benchmarkQueryBus(boolean closeSubscription) {
        String aggId = UUID.randomUUID().toString();

        DemoQuery q = new DemoQuery(aggId);

        SubscriptionQueryResult<DemoQueryResult, DemoQueryResult> result =
                queryGateway.subscriptionQuery(
                        q,
                        ResponseTypes.instanceOf(DemoQueryResult.class),
                        ResponseTypes.instanceOf(DemoQueryResult.class)
                );

        queryBus.queryUpdateEmitter().emit(DemoQuery.class,
                dq -> dq.equals(q),
                new DemoQueryResult(aggId));

        result.updates().blockFirst(Duration.ofSeconds(5L));

        if (closeSubscription)
            result.close();
    }
}
