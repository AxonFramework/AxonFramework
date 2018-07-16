package org.axonframework.boot;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SupportedCommandNamesAware;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.WebClientAutoConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@ContextConfiguration(classes = AxonAutoConfigurationTest.Context.class)
@EnableAutoConfiguration(exclude = {JmxAutoConfiguration.class, WebClientAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class})
@RunWith(SpringRunner.class)
public class AxonHandlerConfigurationTest {

    @Autowired
    private QueryGateway queryGateway;

    @Autowired
    private CommandGateway commandGateway;

    @Test
    public void testMessageRoutedToCorrectMethod() throws Exception {
        assertEquals("Command: info", commandGateway.send("info").get());
        assertEquals("Query: info", queryGateway.query("info", String.class).get());
    }

    @SuppressWarnings("unused")
    @Component
    public static class CommandAndQueryHandler {

        @CommandHandler
        public String handle(String command) {
            return "Command: " + command;
        }

        @QueryHandler
        public String query(String query) {
            return "Query: " + query;
        }

    }
}
