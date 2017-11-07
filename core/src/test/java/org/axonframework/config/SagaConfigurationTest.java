package org.axonframework.config;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.*;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class SagaConfigurationTest {

    private Configuration configuration;

    @Before
    public void setUp() throws Exception {
        configuration = spy(DefaultConfigurer.defaultConfiguration().start());
    }

    @Test
    public void testCreateTrackingSagaManager() throws Exception {
        SagaConfiguration<Object> config = SagaConfiguration.trackingSagaManager(Object.class);
        config.initialize(configuration);
        EventProcessor actual = config.getProcessor();
        assertEquals(TrackingEventProcessor.class, actual.getClass());
        assertEquals("ObjectProcessor", actual.getName());
        // make sure the event bus was used as message source
        verify(configuration).eventBus();
        verify(configuration).messageMonitor(EventProcessor.class, actual.getName());
        verify(configuration).getComponent(eq(RollbackConfiguration.class), any());
        verify(configuration).getComponent(eq(TokenStore.class), any());
        verify(configuration).getComponent(eq(TransactionManager.class), any());
        verify(configuration).getComponent(eq(ErrorHandler.class), any());
        verify(configuration).getComponent(eq(TrackingEventProcessorConfiguration.class), any());
        verify(configuration).getComponent(eq(SagaStore.class), any());
    }

    @Test
    public void testCreateSubscribingProcessor() {
        SagaConfiguration<Object> config = SagaConfiguration.subscribingSagaManager(Object.class);
        config.initialize(configuration);
        EventProcessor actual = config.getProcessor();
        assertEquals(SubscribingEventProcessor.class, actual.getClass());
        assertEquals("ObjectProcessor", actual.getName());
        // make sure the event bus was used as message source
        verify(configuration).eventBus();
        verify(configuration).messageMonitor(EventProcessor.class, actual.getName());
        verify(configuration).getComponent(eq(RollbackConfiguration.class), any());
        verify(configuration).getComponent(eq(ErrorHandler.class), any());
        verify(configuration).getComponent(eq(SagaStore.class), any());

        verify(configuration, never()).getComponent(eq(TransactionManager.class), any());
        verify(configuration, never()).getComponent(eq(TokenStore.class), any());
        verify(configuration, never()).getComponent(eq(TrackingEventProcessorConfiguration.class), any());
    }

    @Test
    public void testCreateTrackingProcessorWithCustomSettings() {
        InMemorySagaStore sagaStore = new InMemorySagaStore();
        SagaConfiguration<Object> config = SagaConfiguration.trackingSagaManager(Object.class, configuration -> new SimpleEventBus())
                .configureTrackingProcessor(c ->  TrackingEventProcessorConfiguration.forSingleThreadedProcessing())
                .configureSagaStore(c -> sagaStore)
                .configureMessageMonitor(c -> NoOpMessageMonitor.instance())
                .configureRollbackConfiguration(c -> RollbackConfigurationType.ANY_THROWABLE)
                .configureErrorHandler(c -> PropagatingErrorHandler.INSTANCE)
                .configureTransactionManager(c -> NoTransactionManager.instance())
                .configureTokenStore(c -> new InMemoryTokenStore());
        config.initialize(configuration);
        EventProcessor actual = config.getProcessor();
        assertEquals(TrackingEventProcessor.class, actual.getClass());
        assertEquals("ObjectProcessor", actual.getName());

        verify(configuration, never()).messageMonitor(EventProcessor.class, actual.getName());
        verify(configuration, never()).getComponent(eq(RollbackConfiguration.class), any());
        verify(configuration, never()).getComponent(eq(TokenStore.class), any());
        verify(configuration, never()).getComponent(eq(TransactionManager.class), any());
        verify(configuration, never()).getComponent(eq(ErrorHandler.class), any());

        verify(configuration, never()).getComponent(eq(SagaStore.class), any());
        verify(configuration, never()).eventBus();
        verify(configuration, never()).getComponent(eq(TrackingEventProcessorConfiguration.class), any());

        assertEquals(sagaStore, config.getSagaStore());
        assertEquals(AnnotatedSagaRepository.class, config.getSagaRepository().getClass());
        assertEquals(AnnotatedSagaManager.class, config.getSagaManager().getClass());
    }

    @Test
    public void testProcessorLifecycle() throws Exception {
        SagaConfiguration<Object> config = SagaConfiguration.trackingSagaManager(Object.class, configuration -> new SimpleEventBus());
    }
}
