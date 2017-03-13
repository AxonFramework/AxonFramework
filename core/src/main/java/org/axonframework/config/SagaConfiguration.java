package org.axonframework.config;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.repository.AnnotatedSagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Module Configuration implementation that defines a Saga. This component allows the configuration of the type of
 * Event Processor used, as well as where to store Saga instances.
 */
public class SagaConfiguration<S> implements ModuleConfiguration {

    private final Component<EventProcessor> processor;
    private final Component<AnnotatedSagaManager<S>> sagaManager;
    private final Component<SagaRepository<S>> sagaRepository;
    private final Component<SagaStore<? super S>> sagaStore;
    private final List<Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>>> handlerInterceptors = new ArrayList<>();
    private Configuration config;

    /**
     * Initialize a configuration for a Saga of given {@code sagaType}, using a Subscribing Event Processor to process
     * incoming Events.
     *
     * @param sagaType The type of Saga to handle events with
     * @param <S>      The type of Saga configured in this configuration
     * @return a SagaConfiguration instance, ready for further configuration
     */
    public static <S> SagaConfiguration<S> subscribingSagaManager(Class<S> sagaType) {
        return new SagaConfiguration<>(sagaType);
    }


    /**
     * Initialize a configuration for a Saga of given {@code sagaType}, using a Tracking Event Processor to process
     * incoming Events. Note that a Token Store should be configured in the global configuration, or the Saga Manager
     * will default to an in-memory token store, which is not recommended for production environments.
     *
     * @param sagaType The type of Saga to handle events with
     * @param <S>      The type of Saga configured in this configuration
     * @return a SagaConfiguration instance, ready for further configuration
     */
    public static <S> SagaConfiguration<S> trackingSagaManager(Class<S> sagaType) {
        SagaConfiguration<S> configuration = new SagaConfiguration<>(sagaType);
        configuration.processor.update(c -> {
            TrackingEventProcessor processor = new TrackingEventProcessor(
                    sagaType.getSimpleName() + "Processor",
                    configuration.sagaManager.get(),
                    c.eventBus(),
                    c.getComponent(TokenStore.class, InMemoryTokenStore::new),
                    c.getComponent(TransactionManager.class, NoTransactionManager::instance));
            processor.registerInterceptor(new CorrelationDataInterceptor<>(c.correlationDataProviders()));
            return processor;
        });
        return configuration;
    }

    @SuppressWarnings("unchecked")
    private SagaConfiguration(Class<S> sagaType) {
        String managerName = sagaType.getSimpleName() + "Manager";
        String processorName = sagaType.getSimpleName() + "Processor";
        String repositoryName = sagaType.getSimpleName() + "Repository";
        sagaStore = new Component<>(() -> config, "sagaStore", c -> c.getComponent(SagaStore.class, InMemorySagaStore::new));
        sagaRepository = new Component<>(() -> config, repositoryName,
                                         c -> new AnnotatedSagaRepository<>(sagaType, sagaStore.get(), c.resourceInjector(),
                                                                            c.parameterResolverFactory()));
        sagaManager = new Component<>(() -> config, managerName, c -> new AnnotatedSagaManager<>(sagaType, sagaRepository.get(),
                                                                                                 c.parameterResolverFactory()));
        processor = new Component<>(() -> config, processorName,
                                    c -> {
                                        SubscribingEventProcessor processor = new SubscribingEventProcessor(managerName, sagaManager.get(), c.eventBus());
                                        processor.registerInterceptor(new CorrelationDataInterceptor<>(c.correlationDataProviders()));
                                        return processor;
                                    });
    }

    /**
     * Configures the Saga Store to use to store Saga instances of this type. By default, Sagas are stored in the
     * Saga Store configured in the global Configuration. This method can be used to override the store for specific
     * Sagas.
     *
     * @param sagaStoreBuilder The builder that returnes a fully initialized Saga Store instance based on the global
     *                         Configuration
     * @return this SagaConfiguration instance, ready for further configuration
     */
    public SagaConfiguration<S> configureSagaStore(Function<Configuration, SagaStore<? super S>> sagaStoreBuilder) {
        sagaStore.update(sagaStoreBuilder);
        return this;
    }

    public SagaConfiguration<S> registerHandlerInterceptor(Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptor) {
        if (config != null) {
            processor.get().registerInterceptor(handlerInterceptor.apply(config));
        } else {
            handlerInterceptors.add(handlerInterceptor);
        }
        return this;
    }

    @Override
    public void initialize(Configuration config) {
        this.config = config;
        for (Function<Configuration, MessageHandlerInterceptor<? super EventMessage<?>>> handlerInterceptor : handlerInterceptors) {
            processor.get().registerInterceptor(handlerInterceptor.apply(config));
        }
    }

    @Override
    public void start() {
        processor.get().start();
    }

    @Override
    public void shutdown() {
        processor.get().shutDown();
    }

}
