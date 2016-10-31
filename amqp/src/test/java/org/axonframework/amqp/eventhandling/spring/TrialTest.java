package org.axonframework.amqp.eventhandling.spring;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration(classes = TrialTest.Context.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class TrialTest {

    @Test
    public void name() throws Exception {
        Assume.assumeTrue(false);

    }

    @Configuration
    public static class Context {

    }
}
