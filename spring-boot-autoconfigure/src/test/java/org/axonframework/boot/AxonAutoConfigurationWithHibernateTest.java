package org.axonframework.boot;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
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

@ContextConfiguration(classes = {DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        AxonAutoConfiguration.class})
@RunWith(SpringRunner.class)
public class AxonAutoConfigurationWithHibernateTest {

    @Autowired
    private ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void testContextInitialization() throws Exception {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertNotNull(applicationContext.getBean(TokenStore.class));
        assertNotNull(applicationContext.getBean(JpaEventStorageEngine.class));
        assertNotNull(applicationContext.getBean(EntityManagerProvider.class));

        assertEquals(5, entityManager.getEntityManagerFactory().getMetamodel().getEntities().size());
    }

    @Test
    public void testEventStorageEngingeUsesSerializerBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final JpaEventStorageEngine engine = applicationContext.getBean(JpaEventStorageEngine.class);

        assertEquals(serializer, engine.getSerializer());
    }
}
