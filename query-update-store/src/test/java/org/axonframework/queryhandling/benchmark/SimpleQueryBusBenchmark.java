package org.axonframework.queryhandling.benchmark;

import org.axonframework.queryhandling.SimpleQueryBus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("spring-test-hsqldb")
public class SimpleQueryBusBenchmark extends AbstractQueryBusBenchmark {

    @Autowired
    public void setSimpleQueryBus(SimpleQueryBus queryBus) {
        super.setQueryBus(queryBus);
    }
}
