/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.legacyjpa.EntityManagerProvider;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.NullLockFactory;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.*;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandler;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.legacyjpa.GenericJpaRepository;
import org.axonframework.modelling.saga.ResourceInjector;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.spring.config.annotation.SpringContextHandlerDefinitionBuilder;
import org.axonframework.spring.config.annotation.SpringContextParameterResolverFactoryBuilder;
import org.axonframework.spring.eventsourcing.SpringPrototypeAggregateFactory;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.spring.saga.SpringResourceInjector;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.transaction.PlatformTransactionManager;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.axonframework.common.ReflectionUtils.methodsOf;
import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.axonframework.spring.SpringUtils.isQualifierMatch;
import static org.axonframework.spring.config.SpringAggregateLookup.buildAggregateHierarchy;
import static org.springframework.beans.factory.BeanFactoryUtils.beanNamesForTypeIncludingAncestors;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;

/**
 * ImportBeanDefinitionRegistrar implementation that sets up an infrastructure Configuration based on beans available
 * in the application context.
 * <p>
 * This component is backed by a DefaultConfiguration (see {@link DefaultConfigurer#defaultConfiguration()}
 * and registers the following beans if present in the ApplicationContext:
 * <ul>
 * <li>{@link CommandBus}</li>
 * <li>{@link EventStorageEngine} or {@link EventBus}</li>
 * <li>{@link Serializer}</li>
 * <li>{@link TokenStore}</li>
 * <li>{@link PlatformTransactionManager}</li>
 * <li>{@link TransactionManager}</li>
 * <li>{@link SagaStore}</li>
 * <li>{@link ResourceInjector} (which defaults to {@link SpringResourceInjector}</li>
 * </ul>
 * <p>
 * Furthermore, all beans with an {@link Aggregate @Aggregate} or {@link Saga @Saga} annotation are inspected and
 * required components to operate the Aggregate or Saga are registered.
 *
 * @author Allard Buijze
 * @since 3.0
 * @deprecated Replaced by the {@link SpringConfigurer} and {@link SpringAxonConfiguration}.
 */
@Deprecated
public class SpringAxonAutoConfigurer implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

    /**
     * Name of the {@link AxonConfiguration} bean.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String AXON_CONFIGURATION_BEAN = "org.axonframework.spring.config.AxonConfiguration";

    /**
     * Name of the {@link Configurer} bean.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String AXON_CONFIGURER_BEAN = "org.axonframework.config.Configurer";

    private static final Logger logger = LoggerFactory.getLogger(SpringAxonAutoConfigurer.class);

    private static final String EMPTY_STRING = "";

    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void registerBeanDefinitions(@Nonnull AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        registry.registerBeanDefinition("commandHandlerSubscriber",
                                        genericBeanDefinition(CommandHandlerSubscriber.class).getBeanDefinition());

        registry.registerBeanDefinition("queryHandlerSubscriber",
                                        genericBeanDefinition(QueryHandlerSubscriber.class).getBeanDefinition());

        Configurer configurer = DefaultConfigurer.defaultConfiguration(false);

        RuntimeBeanReference parameterResolver =
                SpringContextParameterResolverFactoryBuilder.getBeanReference(registry);
        configurer.registerComponent(ParameterResolverFactory.class, c -> beanFactory
                .getBean(parameterResolver.getBeanName(), ParameterResolverFactory.class));

        RuntimeBeanReference handlerDefinition =
                SpringContextHandlerDefinitionBuilder.getBeanReference(registry);
        configurer.registerHandlerDefinition((c, clazz) -> beanFactory
                .getBean(handlerDefinition.getBeanName(), HandlerDefinition.class));

        registerComponent(CommandBus.class, configurer::configureCommandBus, configurer, Configuration::commandBus);
        registerComponent(QueryBus.class, configurer::configureQueryBus, configurer, Configuration::queryBus);
        registerComponent(QueryUpdateEmitter.class, configurer::configureQueryUpdateEmitter);
        registerComponent(
                EventStorageEngine.class, configurer::configureEmbeddedEventStore, configurer, Configuration::eventBus
        );
        registerComponent(EventBus.class, configurer::configureEventBus);
        registerComponent(Serializer.class, configurer::configureSerializer);
        registerComponent(Serializer.class, "eventSerializer", configurer::configureEventSerializer);
        registerComponent(Serializer.class, "messageSerializer", configurer::configureMessageSerializer);
        registerComponent(TokenStore.class, configurer);
        try {
            findComponent(PlatformTransactionManager.class).ifPresent(
                    ptm -> configurer.configureTransactionManager(c -> new SpringTransactionManager(getBean(ptm, c)))
            );
        } catch (NoClassDefFoundError error) {
            // that's fine...
        }
        registerComponent(TransactionManager.class, configurer::configureTransactionManager);
        registerComponent(SagaStore.class, configurer);
        registerComponent(ListenerInvocationErrorHandler.class, configurer);
        registerComponent(ErrorHandler.class, configurer);
        registerComponent(TagsConfiguration.class, configurer);
        String resourceInjector = findComponent(
                ResourceInjector.class, registry,
                () -> genericBeanDefinition(SpringResourceInjector.class).getBeanDefinition()
        );
        configurer.configureResourceInjector(c -> getBean(resourceInjector, c));
        registerComponent(ScopeAwareProvider.class, configurer);
        registerComponent(DeadlineManager.class, configurer, Configuration::deadlineManager);
        registerComponent(EventScheduler.class, configurer, Configuration::eventScheduler);

        EventProcessingModule eventProcessingModule = new EventProcessingModule();
        Optional<String> eventProcessingConfigurerOptional = findComponent(EventProcessingConfigurer.class);
        String eventProcessingConfigurerBeanName = eventProcessingConfigurerOptional
                .orElse("eventProcessingConfigurer");
        if (!eventProcessingConfigurerOptional.isPresent()) {
            registry.registerBeanDefinition(eventProcessingConfigurerBeanName,
                                            genericBeanDefinition(EventProcessingConfigurer.class,
                                                                  () -> eventProcessingModule)
                                                    .getBeanDefinition());
        }

        registerModuleConfigurations(configurer);
        registerCorrelationDataProviders(configurer);
        registerEventUpcasters(configurer);
        registerAggregateBeanDefinitions(configurer, registry);

        String eventProcessingConfigurationName = findComponent(EventProcessingConfiguration.class)
                .orElseThrow(() -> new AxonConfigurationException("Missing EventProcessingConfiguration bean"));

        registry.registerBeanDefinition(AXON_CONFIGURER_BEAN,
                                        genericBeanDefinition(ConfigurerFactoryBean.class)
                                                .addConstructorArgValue(configurer).getBeanDefinition());
        registry.registerBeanDefinition(AXON_CONFIGURATION_BEAN, genericBeanDefinition(AxonConfiguration.class)
                .addConstructorArgReference(AXON_CONFIGURER_BEAN).getBeanDefinition());
        try {
            EventProcessingConfigurer eventProcessingConfigurer = configurer.eventProcessing();
            registerSagaBeanDefinitions(eventProcessingConfigurer);
            registerEventHandlerRegistrar(eventProcessingConfigurationName,
                                          eventProcessingConfigurerBeanName,
                                          registry);
        } catch (AxonConfigurationException ace) {
            logger.warn(
                    "There are several EventProcessingConfigurers registered, Axon will not automatically register sagas and event handlers.",
                    ace);
        }
    }

    private void registerCorrelationDataProviders(Configurer configurer) {
        configurer.configureCorrelationDataProviders(
                c -> {
                    String[] correlationDataProviderBeans =
                            beanFactory.getBeanNamesForType(CorrelationDataProvider.class);
                    return Arrays.stream(correlationDataProviderBeans)
                                 .map(n -> (CorrelationDataProvider) getBean(n, c))
                                 .collect(Collectors.toList());
                });
    }

    private void registerEventUpcasters(Configurer configurer) {
        Arrays.stream(beanFactory.getBeanNamesForType(EventUpcaster.class))
              .collect(Collectors.toMap(
                      upcasterBeanName -> upcasterBeanName,
                      upcasterBeanName -> ObjectUtils.getOrDefault(
                              beanFactory.findAnnotationOnBean(upcasterBeanName, Order.class),
                              Order::value, Ordered.LOWEST_PRECEDENCE
                      )
              ))
              .entrySet().stream()
              .sorted(Map.Entry.comparingByValue())
              .forEach(upcasterEntry -> configurer.registerEventUpcaster(c -> getBean(upcasterEntry.getKey(), c)));
    }

    @SuppressWarnings("unchecked")
    private <T> T getBean(String beanName, Configuration configuration) {
        return (T) configuration.getComponent(ApplicationContext.class).getBean(beanName);
    }

    private void registerEventHandlerRegistrar(String epConfigurationBeanName, String epConfigurerBeanName,
                                               BeanDefinitionRegistry registry) {
        List<RuntimeBeanReference> beans = new ManagedList<>();
        beanFactory.getBeanNamesIterator().forEachRemaining(bean -> {
            if (!beanFactory.isFactoryBean(bean)) {
                Class<?> beanType = beanFactory.getType(bean);
                if (beanType != null && beanFactory.containsBeanDefinition(bean) &&
                        beanFactory.getBeanDefinition(bean).isSingleton()) {
                    boolean hasHandler =
                            StreamSupport.stream(methodsOf(beanType).spliterator(), false)
                                         .map(m -> findAnnotationAttributes(m, MessageHandler.class).orElse(null))
                                         .filter(Objects::nonNull)
                                         .anyMatch(attr -> EventMessage.class
                                                 .isAssignableFrom((Class) attr.get("messageType")));
                    if (hasHandler) {
                        beans.add(new RuntimeBeanReference(bean));
                    }
                }
            }
        });
        registry.registerBeanDefinition("eventHandlerRegistrar", genericBeanDefinition(EventHandlerRegistrar.class)
                .addConstructorArgReference(AXON_CONFIGURATION_BEAN)
                .addConstructorArgReference(epConfigurationBeanName)
                .addConstructorArgReference(epConfigurerBeanName)
                .addPropertyValue("eventHandlers", beans).getBeanDefinition());
    }

    private void registerModuleConfigurations(Configurer configurer) {
        String[] moduleConfigurations = beanFactory.getBeanNamesForType(ModuleConfiguration.class);
        for (String moduleConfiguration : moduleConfigurations) {
            configurer.registerModule(new LazyRetrievedModuleConfiguration(
                    () -> beanFactory.getBean(moduleConfiguration, ModuleConfiguration.class),
                    beanFactory.getType(moduleConfiguration)
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void registerSagaBeanDefinitions(EventProcessingConfigurer configurer) {
        String[] sagas = beanFactory.getBeanNamesForAnnotation(Saga.class);
        for (String saga : sagas) {
            Saga sagaAnnotation = beanFactory.findAnnotationOnBean(saga, Saga.class);
            Class<?> sagaType = beanFactory.getType(saga);
            ProcessingGroup processingGroupAnnotation =
                    beanFactory.findAnnotationOnBean(saga, ProcessingGroup.class);
            if (processingGroupAnnotation != null && nonEmptyBeanName(processingGroupAnnotation.value())) {
                configurer.assignHandlerTypesMatching(processingGroupAnnotation.value(), sagaType::equals);
            }
            configurer.registerSaga(sagaType, sagaConfigurer -> {
                if (sagaAnnotation != null && nonEmptyBeanName(sagaAnnotation.sagaStore())) {
                    sagaConfigurer.configureSagaStore(c -> beanFactory.getBean(sagaAnnotation.sagaStore(), SagaStore.class));
                }
            });
        }
    }

    /**
     * @param <A> generic specifying the Aggregate type being registered
     */
    @SuppressWarnings("unchecked")
    private <A> void registerAggregateBeanDefinitions(Configurer configurer, BeanDefinitionRegistry registry) {
        String[] aggregates = beanFactory.getBeanNamesForAnnotation(Aggregate.class);
        Map<SpringAggregateLookup.SpringAggregate<? super A>, Map<Class<? extends A>, String>> hierarchy = buildAggregateHierarchy(beanFactory, aggregates);
        for (Map.Entry<SpringAggregateLookup.SpringAggregate<? super A>, Map<Class<? extends A>, String>> aggregate : hierarchy.entrySet()) {
            Class<A> aggregateType = (Class<A>) aggregate.getKey().getClassType();
            String aggregatePrototype = aggregate.getKey().getBeanName();
            Aggregate aggregateAnnotation = aggregateType.getAnnotation(Aggregate.class);
            AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(aggregateType);
            aggregateConfigurer.withSubtypes(aggregate.getValue().keySet());

            if (EMPTY_STRING.equals(aggregateAnnotation.repository())) {
                String repositoryName = lcFirst(aggregateType.getSimpleName()) + "Repository";
                String factoryName =
                        aggregatePrototype.substring(0, 1).toLowerCase()
                                + aggregatePrototype.substring(1) + "AggregateFactory";
                if (beanFactory.containsBean(repositoryName)) {
                    aggregateConfigurer.configureRepository(c -> beanFactory.getBean(repositoryName, Repository.class));
                } else {
                    registry.registerBeanDefinition(repositoryName,
                                                    genericBeanDefinition(RepositoryFactoryBean.class)
                                                            .addConstructorArgValue(aggregateConfigurer)
                                                            .getBeanDefinition());

                    if (!registry.isBeanNameInUse(factoryName)) {
                        registry.registerBeanDefinition(factoryName,
                                                        genericBeanDefinition(SpringPrototypeAggregateFactory.class)
                                                                .addConstructorArgValue(aggregatePrototype)
                                                                .addConstructorArgValue(aggregate.getValue())
                                                                .getBeanDefinition());
                    }
                    aggregateConfigurer.configureAggregateFactory(
                            c -> beanFactory.getBean(factoryName, AggregateFactory.class)
                    );

                    String triggerDefinitionBeanName = aggregateAnnotation.snapshotTriggerDefinition();
                    if (nonEmptyBeanName(triggerDefinitionBeanName)) {
                        aggregateConfigurer.configureSnapshotTrigger(
                                c -> beanFactory.getBean(triggerDefinitionBeanName, SnapshotTriggerDefinition.class)
                        );
                    }

                    String cacheBeanName = aggregateAnnotation.cache();
                    if (nonEmptyBeanName(cacheBeanName)) {
                        aggregateConfigurer.configureCache(c -> beanFactory.getBean(cacheBeanName, Cache.class));
                    }

                    String lockFactoryBeanName = aggregateAnnotation.lockFactory();
                    if (nonEmptyBeanName(lockFactoryBeanName)) {
                        aggregateConfigurer.configureLockFactory(
                                c -> beanFactory.getBean(lockFactoryBeanName, LockFactory.class)
                        );
                    }

                    if (AnnotationUtils.isAnnotationPresent(aggregateType, "javax.persistence.Entity")) {
                        aggregateConfigurer.configureRepository(
                                c -> GenericJpaRepository.builder(aggregateType)
                                                         .parameterResolverFactory(c.parameterResolverFactory())
                                                         .handlerDefinition(c.handlerDefinition(aggregateType))
                                                         .lockFactory(c.getComponent(
                                                                 LockFactory.class, () -> NullLockFactory.INSTANCE
                                                         ))
                                                         .entityManagerProvider(c.getComponent(
                                                                 EntityManagerProvider.class,
                                                                 () -> beanFactory.getBean(EntityManagerProvider.class)
                                                         ))
                                                         .eventBus(c.eventBus())
                                                         .repositoryProvider(c::repository)
                                                         .build()
                        );
                    }
                }
            } else {
                aggregateConfigurer.configureRepository(
                        c -> beanFactory.getBean(aggregateAnnotation.repository(), Repository.class)
                );
            }

            String snapshotFilterBeanName = aggregateAnnotation.snapshotFilter();
            if (nonEmptyBeanName(snapshotFilterBeanName)) {
                aggregateConfigurer.configureSnapshotFilter(c -> getBean(snapshotFilterBeanName, c));
            }

            String commandTargetResolverBeanName = aggregateAnnotation.commandTargetResolver();
            if (nonEmptyBeanName(commandTargetResolverBeanName)) {
                aggregateConfigurer.configureCommandTargetResolver(
                        c -> getBean(commandTargetResolverBeanName, c)
                );
            } else {
                findComponent(CommandTargetResolver.class).ifPresent(
                        commandTargetResolver -> aggregateConfigurer.configureCommandTargetResolver(
                                c -> getBean(commandTargetResolver, c)
                        )
                );
            }

            aggregateConfigurer.configureFilterEventsByType(c -> aggregateAnnotation.filterEventsByType());

            configurer.configureAggregate(aggregateConfigurer);
        }
    }

    private boolean nonEmptyBeanName(String beanName) {
        return !EMPTY_STRING.equals(beanName);
    }

    /**
     * Return the given {@code string}, with its first character lowercase
     *
     * @param string The input string
     *
     * @return The input string, with first character lowercase
     */
    private String lcFirst(String string) {
        return string.substring(0, 1).toLowerCase() + string.substring(1);
    }

    private <T> String findComponent(Class<T> componentType,
                                     BeanDefinitionRegistry registry,
                                     Supplier<BeanDefinition> defaultBean) {
        return findComponent(componentType).orElseGet(() -> {
            BeanDefinition beanDefinition = defaultBean.get();
            String beanName = BeanDefinitionReaderUtils.generateBeanName(beanDefinition, registry);
            registry.registerBeanDefinition(beanName, beanDefinition);
            return beanName;
        });
    }

    /**
     * Register a component of {@code componentType} with {@code componentQualifier} through the given {@code
     * registrationFunction}. The component to register will be a bean retrieved from the {@link ApplicationContext}
     * tied to the {@link Configuration}.
     *
     * @param componentType        the type of the component to register
     * @param componentQualifier   the qualifier of the component to register
     * @param registrationFunction the function to register the component to the {@link Configuration}
     * @param <T>                  the type of the component
     */
    private <T> void registerComponent(Class<T> componentType,
                                       String componentQualifier,
                                       Consumer<Function<Configuration, T>> registrationFunction) {
        findComponent(componentType, componentQualifier).ifPresent(
                componentName -> registrationFunction.accept(config -> getBean(componentName, config))
        );
    }

    private <T> Optional<String> findComponent(Class<T> componentType, String componentQualifier) {
        return Stream.of(beanNamesForTypeIncludingAncestors(beanFactory, componentType))
                     .filter(bean -> isQualifierMatch(bean, beanFactory, componentQualifier))
                     .findFirst();
    }

    /**
     * Register a component of {@code componentType} through the given {@code registrationFunction}. The component to
     * register will be a bean retrieved from the {@link ApplicationContext} tied to the {@link Configuration}.
     *
     * @param componentType        the type of the component to register
     * @param registrationFunction the function to register the component to the {@link Configuration}
     * @param <T>                  the type of the component
     */
    private <T> void registerComponent(Class<T> componentType,
                                       Consumer<Function<Configuration, T>> registrationFunction) {
        findComponent(componentType).ifPresent(
                componentName -> registrationFunction.accept(config -> getBean(componentName, config))
        );
    }

    /**
     * Register a component of {@code componentType} with the given {@code configurer} through {@link
     * Configurer#registerComponent(Class, Function)}. The component to register will be a bean retrieved from the
     * {@link ApplicationContext} tied to the {@link Configuration}.
     *
     * @param componentType the type of the component to register
     * @param configurer    the {@link Configurer} used to register the component with
     * @param <T>           the type of the component
     */
    private <T> void registerComponent(Class<T> componentType, Configurer configurer) {
        registerComponent(componentType,
                          builder -> configurer.registerComponent(componentType, builder),
                          configurer,
                          null);
    }

    /**
     * Register a component of {@code componentType} with the given {@code configurer}. through {@link
     * Configurer#registerComponent(Class, Function)}. The {@code initHandler} is used to initialize the component at
     * the right point in time. The component to register will be a bean retrieved from the {@link ApplicationContext}
     * tied to the {@link Configuration}.
     *
     * @param componentType the type of the component to register
     * @param configurer    the {@link Configurer} used to register the component with
     * @param initHandler   the function used to initialize the registered component
     * @param <T>           the type of the component
     */
    private <T> void registerComponent(Class<T> componentType,
                                       Configurer configurer,
                                       Consumer<Configuration> initHandler) {
        registerComponent(componentType,
                          builder -> configurer.registerComponent(componentType, builder),
                          configurer,
                          initHandler);
    }

    /**
     * Register a component of {@code componentType} through the given {@code registrationFunction}. The {@code
     * initHandler} is used to initialize the component at the right point in time. The component to register will be a
     * bean retrieved from the {@link ApplicationContext} tied to the {@link Configuration}.
     *
     * @param componentType        the type of the component to register
     * @param registrationFunction the function to register the component to the {@link Configuration}
     * @param configurer           the {@link Configurer} used to register the component with
     * @param initHandler          the function used to initialize the registered component
     * @param <T>                  the type of the component
     */
    private <T> void registerComponent(Class<T> componentType,
                                       Consumer<Function<Configuration, T>> registrationFunction,
                                       Configurer configurer,
                                       Consumer<Configuration> initHandler) {
        findComponent(componentType).ifPresent(componentName -> {
            registrationFunction.accept(config -> getBean(componentName, config));
            if (initHandler != null) {
                configurer.onInitialize(c -> c.onStart(Integer.MIN_VALUE, () -> initHandler.accept(c)));
            }
        });
    }

    private <T> Optional<String> findComponent(Class<T> componentType) {
        String[] beans = beanNamesForTypeIncludingAncestors(beanFactory, componentType);
        if (beans.length == 1) {
            return Optional.of(beans[0]);
        } else if (beans.length > 1) {
            for (String bean : beans) {
                BeanDefinition beanDef = beanFactory.getMergedBeanDefinition(bean);
                if (beanDef.isPrimary()) {
                    return Optional.of(bean);
                }
            }
            logger.warn("Multiple beans of type {} found in application context: {}. Chose {}",
                        componentType.getSimpleName(), beans, beans[0]);
            return Optional.of(beans[0]);
        }
        return Optional.empty();
    }

    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    /**
     * Implementation of an {@link ImportSelector} that enables the import of the {@link SpringAxonAutoConfigurer} after
     * all {@code @Configuration} beans have been processed.
     */
    public static class ImportSelector implements DeferredImportSelector {

        @Nonnull
        @Override
        public String[] selectImports(@Nonnull AnnotationMetadata importingClassMetadata) {
            return new String[]{SpringAxonAutoConfigurer.class.getName()};
        }
    }

    private static class LazyRetrievedModuleConfiguration implements ModuleConfiguration {

        private final Supplier<ModuleConfiguration> delegateSupplier;
        private final Class<?> moduleType;
        private ModuleConfiguration delegate;

        LazyRetrievedModuleConfiguration(Supplier<ModuleConfiguration> delegateSupplier, Class<?> moduleType) {
            this.delegateSupplier = delegateSupplier;
            this.moduleType = moduleType;
        }

        @Override
        public void initialize(Configuration config) {
            getDelegate().initialize(config);
        }

        @Override
        public ModuleConfiguration unwrap() {
            return getDelegate();
        }

        @Override
        public boolean isType(Class<?> type) {
            return type.isAssignableFrom(moduleType);
        }

        private ModuleConfiguration getDelegate() {
            if (delegate == null) {
                delegate = delegateSupplier.get();
            }
            return delegate;
        }
    }

}
