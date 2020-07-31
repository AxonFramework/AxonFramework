package org.axonframework.integrationtests.queryhandling;

import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter;

/**
 * An {@link AbstractSubscriptionQueryTestSuite} implementation validating the {@link SimpleQueryBus}.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 */
public class SimpleQueryBusSubscriptionQueryTest extends AbstractSubscriptionQueryTestSuite {

    private final SimpleQueryUpdateEmitter queryUpdateEmitter = SimpleQueryUpdateEmitter.builder().build();
    private final SimpleQueryBus queryBus = SimpleQueryBus.builder()
                                                          .queryUpdateEmitter(queryUpdateEmitter)
                                                          .build();

    @Override
    public QueryBus queryBus() {
        return queryBus;
    }

    @Override
    public QueryUpdateEmitter queryUpdateEmitter() {
        return queryBus.queryUpdateEmitter();
    }
}
