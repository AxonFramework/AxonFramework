/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventstreaming.StreamableEventSource;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.queryhandling.QueryMessage;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * An implementation of a {@link ConfigurationEnhancer} that will register a list of beans as handlers for a specific
 * type of message.
 * <p>
 * The beans will be lazily resolved to avoid circular dependencies if any these beans relies on the Axon
 * {@link org.axonframework.configuration.Configuration} to be available in the Application Context.
 * <p>
 * Typically, an application context would have an instance of this class registered for each type of message to
 * register.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
@Internal
public class MessageHandlerConfigurer implements ConfigurationEnhancer, ApplicationContextAware {

    private final Type type;
    private final List<String> handlerBeansRefs;
    private ApplicationContext applicationContext;

    /**
     * Registers the beans identified in given {@code beanRefs} as the given {@code type} of handler with the Axon
     * {@link org.axonframework.configuration.Configuration}.
     *
     * @param type     The type of handler to register the beans as.
     * @param beanRefs A list of bean identifiers to register.
     */
    public MessageHandlerConfigurer(Type type, List<String> beanRefs) {
        this.type = type;
        this.handlerBeansRefs = beanRefs;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        switch (type) {
            case EVENT:
                configureEventHandlers(registry);
                break;
            case QUERY:
                // TODO: register query handler registration as a part of #3364
//                handlerBeans.forEach(handler -> configurer.registerQueryHandler(c -> applicationContext.getBean(handler)));
                break;
            case COMMAND:
                configureCommandHandlers(registry);
                break;
        }
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void configureEventHandlers(ComponentRegistry registry) {

        groupNamedBeanDefinitionsByPackage().forEach((packageName, beanDefs) -> {

            Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase> componentRegistration = (phase) -> {
                EventHandlingComponentsConfigurer.AdditionalComponentPhase resultOfRegistration = null;
                for (NamedBeanDefinition namedBeanDefinition : beanDefs) {
                    resultOfRegistration = phase.annotated(this.createComponentBuilder(namedBeanDefinition));
                }
                return resultOfRegistration;
            };

            EventProcessorModule module;
            EventProcessorSettings settings = resolve(packageName);
            if (settings == null) {
                module = EventProcessorModule
                        .pooledStreaming(packageName)
                        .eventHandlingComponents(componentRegistration)
                        .notCustomized();
            } else {
                switch (settings.getProcessorMode()) {
                    case POOLED -> {
                        var moduleSettings = (EventProcessorSettings.PooledEventProcessorSettings) settings;
                        module = EventProcessorModule
                                .pooledStreaming(packageName)
                                .eventHandlingComponents(componentRegistration)
                                .customized((c, pooledStreamConfiguration) -> {
                                    // worker executor
                                    var workerExecutor = Executors.newScheduledThreadPool(
                                            moduleSettings.getThreadCount(),
                                            new AxonThreadFactory("WorkPackage[" + packageName + "]")
                                    );
                                    // FIXME -> can we shut down the executor if the application shuts down?

                                    var config = pooledStreamConfiguration
                                            .workerExecutor(workerExecutor)
                                            .tokenClaimInterval(moduleSettings.getTokenClaimIntervalInMillis())
                                            .batchSize(moduleSettings.getBatchSize())
                                            .initialSegmentCount(moduleSettings.getInitialSegmentCount())
                                            ;
                                    if (moduleSettings.getSource() != null) {
                                        //noinspection unchecked
                                        config = config.eventSource(getTypedBeanByName(
                                                moduleSettings.getSource(),
                                                StreamableEventSource.class,
                                                "Invalid event source [%s] configured for Pooled Streaming Event Processor ["
                                                        + packageName
                                                        + "]."
                                        ));
                                    }
                                    return config;
                                });
                    }
                    case SUBSCRIBING -> {
                        var moduleSettings = (EventProcessorSettings.SubscribingEventProcessorSettings) settings;
                        module = EventProcessorModule
                                .subscribing(packageName)
                                .eventHandlingComponents(componentRegistration)
                                .customized((c, subscribingConfiguration) -> {
                                    var config = subscribingConfiguration;
                                    if (moduleSettings.getSource() != null) {
                                        //noinspection unchecked
                                        config = config.messageSource(getTypedBeanByName(
                                                moduleSettings.getSource(),
                                                SubscribableMessageSource.class,
                                                "Invalid message source [%s] configured for Subscribing Event Processor ["
                                                        + packageName
                                                        + "]."
                                        ));
                                    }
                                    return config;
                                });
                    }
                    default -> throw new IllegalStateException(
                            "Unsupported event handling mode: " + settings.getProcessorMode()
                                    + " for package "
                                    + packageName);
                }
            }
            registry.registerModule(module);
        });
    }

    private void configureCommandHandlers(ComponentRegistry registry) {
        groupNamedBeanDefinitionsByPackage().forEach((packageName, beanDefs) -> {
            var commandHandlingModuleBuilder = CommandHandlingModule
                    .named(packageName)
                    .commandHandlers();
            beanDefs.forEach(namedBeanDefinition -> {
                commandHandlingModuleBuilder
                        .annotatedCommandHandlingComponent(this.createComponentBuilder(namedBeanDefinition));
            });
            registry.registerModule(commandHandlingModuleBuilder.build());
        });
    }

    @Nonnull
    private <T> T getTypedBeanByName(@Nonnull String name, @Nonnull Class<T> clazz, @Nonnull String message) {
        var object = applicationContext.getBean(name);
        if (clazz.isAssignableFrom(object.getClass())) {
            //noinspection unchecked
            return (T) object;
        }
        throw new AxonConfigurationException(format(
                message + " The expected bean should be of type %s, but it was %s.",
                name, clazz.getSimpleName(), object.getClass().getSimpleName()
        ));
    }

    @Nullable
    private EventProcessorSettings resolve(@Nonnull String packageName) {
        Map<String, EventProcessorSettings> allSettings = applicationContext.getBeansOfType(EventProcessorSettings.class);
        return allSettings.get(packageName);
    }


    private ComponentBuilder<Object> createComponentBuilder(@Nonnull NamedBeanDefinition namedBeanDefinition) {
        return (c) -> applicationContext.getBean(namedBeanDefinition.name());
    }

    private Map<String, List<NamedBeanDefinition>> groupNamedBeanDefinitionsByPackage() {

        // We need access to the BeanFactory, so cast to ConfigurableApplicationContext
        var beanFactory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        return handlerBeansRefs.stream()
                               .map(name -> new NamedBeanDefinition(name, beanFactory.getBeanDefinition(name)))
                               .collect(Collectors.groupingBy(nbd -> {
                                   String className = nbd.definition().getBeanClassName();
                                   if (className == null && nbd.definition() instanceof AbstractBeanDefinition) {
                                       Class<?> beanClass = ((AbstractBeanDefinition) nbd.definition()).getBeanClass();
                                       className = beanClass.getName();
                                   }
                                   return (className != null && className.contains("."))
                                           ? className.substring(0, className.lastIndexOf('.'))
                                           : "default";
                               }));
    }

    /**
     * Named bean definition.
     *
     * @param name       name of the bean.
     * @param definition bean definition.
     */
    record NamedBeanDefinition(
            @Nonnull String name,
            @Nonnull BeanDefinition definition
    ) {

    }

    /**
     * Enumeration defining the auto configurable message handler types.
     */
    public enum Type {

        /**
         * Lists the message handler for {@link CommandMessage CommandMessages}.
         */
        COMMAND(CommandMessage.class),
        /**
         * Lists the message handler for {@link EventMessage EventMessages}.
         */
        EVENT(EventMessage.class),
        /**
         * Lists the message handler for {@link QueryMessage QueryMessages}.
         */
        QUERY(QueryMessage.class);

        private final Class<? extends Message> messageType;

        // Suppressed to allow instantiation of enumeration.
        @SuppressWarnings({"rawtypes", "unchecked"})
        Type(Class<? extends Message> messageType) {
            this.messageType = (Class<? extends Message>) messageType;
        }

        /**
         * Returns the supported {@link Message} implementation.
         *
         * @return The supported {@link Message} implementation.
         */
        public Class<? extends Message> getMessageType() {
            return messageType;
        }
    }
}
