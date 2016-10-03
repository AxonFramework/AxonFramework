package org.axonframework.config;

import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;

import java.util.function.Function;

public class SagaConfiguration<S> implements ModuleConfiguration {

    private final Component<EventProcessor> processor;
    private final Component<AnnotatedSagaManager<S>> sagaManager;
    private final Component<SagaRepository<S>> sagaRepository;
    private final Component<SagaStore<? super S>> sagaStore;
    private Configuration config;

    public static <S> SagaConfiguration<S> subscribingSagaManager(Class<S> sagaType) {
        return new SagaConfiguration<S>(sagaType);
    }

    public static <S> SagaConfiguration<S> trackingSagaManager(Class<S> sagaType) {
        SagaConfiguration<S> configuration = new SagaConfiguration<>(sagaType);
        configuration.processor.update(c -> new TrackingEventProcessor(sagaType.getSimpleName() + "Processor",
                                                                       configuration.sagaManager.get(),
                                                                       c.eventBus(), c.getComponent(TokenStore.class)));
        return configuration;
    }

    private SagaConfiguration(Class<S> sagaType) {
        String managerName = sagaType.getSimpleName() + "Manager";
        String processorName = sagaType.getSimpleName() + "Processor";
        String repositoryName = sagaType.getSimpleName() + "Repository";
        sagaStore = new Component<>(() -> config, "sagaStore", c -> c.getComponent(SagaStore.class, InMemorySagaStore::new));
        sagaRepository = new Component<>(() -> config, repositoryName,
                                         c -> new AnnotatedSagaRepository<>(sagaType, sagaStore.get(), c.resourceInjector()));
        sagaManager = new Component<>(() -> config, managerName, c -> new AnnotatedSagaManager<>(sagaType, sagaRepository.get()));
        processor = new Component<>(() -> config, processorName,
                                    c -> new SubscribingEventProcessor(managerName, sagaManager.get(), c.eventBus()));
    }

    public SagaConfiguration<S> configureSagaStore(Function<Configuration, SagaStore<? super S>> sagaStoreBuilder) {
        sagaStore.update(sagaStoreBuilder);
        return this;
    }

    @Override
    public void initialize(Configuration config) {
        this.config = config;
    }

    @Override
    public void start() {
        processor.get().start();
    }

    @Override
    public void shutdown() {
        processor.get().shutdown();
    }

}
