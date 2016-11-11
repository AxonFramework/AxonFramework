package org.axonframework.boot;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.serialization.Serializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@ContextConfiguration(classes = {DataSourceAutoConfiguration.class,
        AxonAutoConfiguration.class})
@RunWith(SpringRunner.class)
public class AxonAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testContextInitialization() throws Exception {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertEquals(0, applicationContext.getBeansOfType(TokenStore.class).size());
    }
}
