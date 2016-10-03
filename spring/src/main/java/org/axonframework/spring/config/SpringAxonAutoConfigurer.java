package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.*;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.saga.ResourceInjector;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.messaging.annotation.MessageHandler;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.serialization.Serializer;
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
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static org.axonframework.common.ReflectionUtils.methodsOf;

public class SpringAxonAutoConfigurer implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

    private static final Logger logger = LoggerFactory.getLogger(SpringAxonAutoConfigurer.class);

    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        registry.registerBeanDefinition("commandHandlerSubscriber",
                                        BeanDefinitionBuilder.genericBeanDefinition(CommandHandlerSubscriber.class)
                                                .getBeanDefinition());

        Configurer configurer = DefaultConfigurer.defaultConfiguration();

        RuntimeBeanReference parameterResolver = SpringContextParameterResolverFactoryBuilder.getBeanReference(registry);
        configurer.registerComponent(ParameterResolverFactory.class,
                                     c -> beanFactory.getBean(parameterResolver.getBeanName(),
                                                              ParameterResolverFactory.class));

        findComponent(CommandBus.class).ifPresent(
                commandBus -> configurer.configureCommandBus(c -> getBean(commandBus, c)));
        findComponent(EventStorageEngine.class).ifPresent(
                ese -> configurer.configureEmbeddedEventStore(c -> getBean(ese, c)));
        findComponent(EventBus.class).ifPresent(
                eventBus -> configurer.configureEventBus(c -> getBean(eventBus, c)));
        findComponent(Serializer.class).ifPresent(
                serializer -> configurer.configureSerializer(c -> getBean(serializer, c)));
        findComponent(TokenStore.class).ifPresent(
                tokenStore -> configurer.registerComponent(TokenStore.class, c -> getBean(tokenStore, c)));
        findComponent(PlatformTransactionManager.class).ifPresent(
                ptm -> configurer.configureTransactionManager(c -> new SpringTransactionManager(getBean(ptm, c))));
        findComponent(TransactionManager.class).ifPresent(
                tm -> configurer.configureTransactionManager(c -> getBean(tm, c)));
        findComponent(SagaStore.class).ifPresent(
                sagaStore -> configurer.registerComponent(SagaStore.class, c -> getBean(sagaStore, c)));
        findComponent(ResourceInjector.class).ifPresent(
                resourceInjector -> configurer.configureResourceInjector(c -> getBean(resourceInjector, c)));

        registerAggregateBeanDefinitions(configurer, registry);
        registerSagaBeanDefinitions(configurer, registry);
        registerModules(configurer);

        if (!findComponent(EventHandlingConfiguration.class).isPresent()) {
            registerDefaultEventHandlerConfiguration(registry);
        }

        registry.registerBeanDefinition("axonConfiguration",
                                        BeanDefinitionBuilder.genericBeanDefinition(AxonConfiguration.class)
                                                .addConstructorArgValue(configurer)
                                                .getBeanDefinition());
    }

    private <T> T getBean(String beanName, Configuration configuration) {
        return (T) configuration.getComponent(ApplicationContext.class).getBean(beanName);
    }

    private void registerDefaultEventHandlerConfiguration(BeanDefinitionRegistry registry) {
        List<RuntimeBeanReference> beans = new ManagedList<>();
        beanFactory.getBeanNamesIterator().forEachRemaining(bean -> {
            Class<?> beanType = beanFactory.getType(bean);
            if (beanFactory.containsBeanDefinition(bean) && beanFactory.getBeanDefinition(bean).isSingleton()) {
                boolean hasHandler = StreamSupport.stream(methodsOf(beanType).spliterator(), false)
                        .map(m -> AnnotationUtils.findAnnotationAttributes(m, MessageHandler.class).orElse(null))
                        .filter(Objects::nonNull)
                        .anyMatch(attr -> EventMessage.class.isAssignableFrom((Class) attr.get("messageType")));
                if (hasHandler) {
                    beans.add(new RuntimeBeanReference(bean));
                }
            }
        });
        registry.registerBeanDefinition("eventHandlingConfiguration",
                                        BeanDefinitionBuilder.genericBeanDefinition(SpringEventHandlingConfiguration.class)
                                                .addPropertyReference("axonConfiguration", "axonConfiguration")
                                                .addPropertyValue("eventHandlers", beans).getBeanDefinition());
    }

    private String[] registerModules(Configurer configurer) {
        // find all modules. If none, create standard event handler module
        String[] modules = beanFactory.getBeanNamesForType(ModuleConfiguration.class);
        for (String module : modules) {
            configurer.registerModule(new LazyRetrievedModuleConfiguration(() -> beanFactory.getBean(module, ModuleConfiguration.class)));
        }
        return modules;
    }

    private void registerSagaBeanDefinitions(Configurer configurer, BeanDefinitionRegistry registry) {
        String[] sagas = beanFactory.getBeanNamesForAnnotation(Saga.class);
        for (String saga : sagas) {
            Saga sagaAnnotation = beanFactory.findAnnotationOnBean(saga, Saga.class);
            SagaConfiguration<?> sagaConfiguration = SagaConfiguration.subscribingSagaManager(beanFactory.getType(saga));

            if (!"".equals(sagaAnnotation.sagaStore())) {
                sagaConfiguration.configureSagaStore(c -> beanFactory.getBean(sagaAnnotation.sagaStore(), SagaStore.class));
            }
            configurer.registerModule(sagaConfiguration);
        }
    }

    private void registerAggregateBeanDefinitions(Configurer configurer, BeanDefinitionRegistry registry) {
        String[] aggregates = beanFactory.getBeanNamesForAnnotation(Aggregate.class);
        for (String aggregate : aggregates) {
            Aggregate aggregateAnnotation = beanFactory.findAnnotationOnBean(aggregate, Aggregate.class);
            AggregateConfigurer<?> aggregateConf = AggregateConfigurer.defaultConfiguration(beanFactory.getType(aggregate));
            if ("".equals(aggregateAnnotation.repository())) {
                String repositoryName = aggregate.substring(0, 1).toLowerCase() + aggregate.substring(1) + "Repository";
                String factoryName = aggregate.substring(0, 1).toLowerCase() + aggregate.substring(1) + "AggregateFactory";
                if (beanFactory.containsBean(repositoryName)) {
                    aggregateConf.configureRepository(c -> beanFactory.getBean(repositoryName, Repository.class));
                } else {
                    if (!registry.isBeanNameInUse(factoryName)) {
                        registry.registerBeanDefinition(factoryName, BeanDefinitionBuilder.genericBeanDefinition(
                                SpringPrototypeAggregateFactory.class)
                                .addPropertyValue("prototypeBeanName", aggregate)
                                .getBeanDefinition());
                    }
                    aggregateConf.configureAggregateFactory(c -> beanFactory.getBean(factoryName, AggregateFactory.class));
                }
            } else {
                aggregateConf.configureRepository(c -> beanFactory.getBean(aggregateAnnotation.repository(), Repository.class));
            }

            configurer.configureAggregate(aggregateConf);
        }
    }

    private <T> Optional<String> findComponent(Class<T> componentType) {
        String[] beans = beanFactory.getBeanNamesForType(componentType);
        if (beans.length == 1) {
            return Optional.of(beans[0]);
        } else if (beans.length > 1) {
            for (String bean : beans) {
                BeanDefinition beanDef = beanFactory.getBeanDefinition(bean);
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
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    public static class ImportSelector implements DeferredImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            return new String[]{SpringAxonAutoConfigurer.class.getName()};
        }
    }

    private class LazyRetrievedModuleConfiguration implements ModuleConfiguration {

        private final Supplier<ModuleConfiguration> delegateSupplier;
        private ModuleConfiguration delegate;

        public LazyRetrievedModuleConfiguration(Supplier<ModuleConfiguration> delegateSupplier) {
            this.delegateSupplier = delegateSupplier;
        }

        @Override
        public void initialize(Configuration config) {
            delegate = delegateSupplier.get();
            delegate.initialize(config);
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }
    }
}
