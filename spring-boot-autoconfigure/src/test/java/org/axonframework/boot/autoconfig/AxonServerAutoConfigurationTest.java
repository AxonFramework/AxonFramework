package org.axonframework.boot.autoconfig;

import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;


@ContextConfiguration
@EnableAutoConfiguration
@RunWith(SpringRunner.class)
public class AxonServerAutoConfigurationTest {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private QueryUpdateEmitter updateEmitter;

    @Test
    public void testAxonServerQueryBusConfiguration() {
        assertTrue(queryBus instanceof AxonServerQueryBus);
        assertSame(updateEmitter, queryBus.queryUpdateEmitter());
    }
}