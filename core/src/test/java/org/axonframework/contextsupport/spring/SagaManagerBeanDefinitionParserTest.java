package org.axonframework.contextsupport.spring;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleCluster;
import org.axonframework.eventhandling.replay.DiscardingIncomingMessageHandler;
import org.axonframework.eventhandling.replay.ReplayingCluster;
import org.axonframework.eventstore.management.EventStoreManagement;
import org.axonframework.saga.SagaManager;
import org.axonframework.unitofwork.NoTransactionManager;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SagaManagerBeanDefinitionParserTest.Context.class)
public class SagaManagerBeanDefinitionParserTest {

    @Autowired
    private EventBus mockEventBus;

    @Qualifier("explicitManager")
    @Autowired
    private SagaManager explicitManager;

    @Qualifier("autowiredManager")
    @Autowired
    private SagaManager autowiredManager;

    @Qualifier("replayableManager")
    @Autowired
    private SagaManager replayableManager;

    @Test
    public void testSagaManagerSubscribedToEventBus() throws Exception {
        verify(mockEventBus).subscribe(autowiredManager);
        verify(mockEventBus).subscribe(explicitManager);

        assertNotNull(explicitManager.getTargetType());
    }

    @Test
    public void testReplayableSagaManager() throws Exception {
        ReplayingCluster cluster = new ReplayingCluster(
                new SimpleCluster("Cluster"), mock(EventStoreManagement.class), new NoTransactionManager(), 10,
                new DiscardingIncomingMessageHandler());
        cluster.subscribe(replayableManager);
        cluster.startReplay();
        assertTrue(true);
    }

    @ImportResource("classpath:/contexts/saga-manager-context.xml")
    @Configuration
    public static class Context {

        @Bean
        public EventBus eventBus() {
            return mock(EventBus.class);
        }
    }
}
