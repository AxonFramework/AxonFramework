package org.axonframework.springboot;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventScheduler;
import org.axonframework.axonserver.connector.processor.EventProcessorControlService;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.RoutingKey;
import org.axonframework.commandhandling.gateway.Timeout;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.Priority;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.config.MessageHandlerRegistrar;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.AllowReplay;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.HasHandlerAttributes;
import org.axonframework.messaging.annotation.MessageHandler;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.AggregateRoot;
import org.axonframework.modelling.command.AggregateVersion;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.saga.repository.jpa.AbstractSagaEntry;
import org.axonframework.modelling.saga.repository.jpa.SagaEntry;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.serialization.ContentTypeConverter;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.BeanFactoryNativeConfigurationProcessor;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeConfigurationRegistry;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeResourcesEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.nativex.hint.TypeAccess;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class AxonNativeHints implements BeanFactoryNativeConfigurationProcessor {

    @Override
    public void process(ConfigurableListableBeanFactory beanFactory, NativeConfigurationRegistry registry) {
        registerServiceLoaderImplementations(registry, HandlerDefinition.class);
        registerServiceLoaderImplementations(registry, HandlerEnhancerDefinition.class);
        registerServiceLoaderImplementations(registry, ParameterResolverFactory.class);
        registerServiceLoaderImplementations(registry, ContentTypeConverter.class);
        registerServiceLoaderImplementations(registry, PropertyAccessStrategy.class);
        registerServiceLoaderImplementations(registry, IdentifierFactory.class);

        registry.reflection().forType(Priority.class).withAccess(TypeAccess.values());
        registry.reflection().forType(AggregateRoot.class).withAccess(TypeAccess.values());
        registry.reflection().forType(CommandHandler.class).withAccess(TypeAccess.values());
        registry.reflection().forType(MessageHandler.class).withAccess(TypeAccess.values());
        registry.reflection().forType(Qualifier.class).withAccess(TypeAccess.values());
        registry.reflection().forType(QueryHandler.class).withAccess(TypeAccess.values());
        registry.reflection().forType(EventHandler.class).withAccess(TypeAccess.values());
        registry.reflection().forType(EventSourcingHandler.class).withAccess(TypeAccess.values());
        registry.reflection().forType(ProcessingGroup.class).withAccess(TypeAccess.values());
        registry.reflection().forType(StartHandler.class).withAccess(TypeAccess.values());
        registry.reflection().forType(ShutdownHandler.class).withAccess(TypeAccess.values());
        registry.reflection().forType(RoutingKey.class).withAccess(TypeAccess.values());
        registry.reflection().forType(Timeout.class).withAccess(TypeAccess.values());
        registry.reflection().forType(MetaDataValue.class).withAccess(TypeAccess.values());
        registry.reflection().forType(AllowReplay.class).withAccess(TypeAccess.values());
        registry.reflection().forType(AggregateMember.class).withAccess(TypeAccess.values());
        registry.reflection().forType(EntityId.class).withAccess(TypeAccess.values());
        registry.reflection().forType(AggregateVersion.class).withAccess(TypeAccess.values());
        registry.reflection().forType(AggregateIdentifier.class).withAccess(TypeAccess.values());
        registry.reflection().forType(TargetAggregateIdentifier.class).withAccess(TypeAccess.values());
        registry.reflection().forType(DeadlineHandler.class).withAccess(TypeAccess.values());
        registry.reflection().forType(HasHandlerAttributes.class).withAccess(TypeAccess.values());
        registry.reflection().forType(SagaEntry.class).withAccess(TypeAccess.values());
        registry.reflection().forType(AbstractSagaEntry.class).withAccess(TypeAccess.values());

        registry.resources().add(NativeResourcesEntry.of("axonserver_download.txt"));
        registry.resources().add(NativeResourcesEntry.of("org/axonframework/eventsourcing/eventstore/jpa/SQLErrorCode.properties"));

        tryRegister(() -> {
            registry.reflection().forType(TrackingEventProcessor.class).withExecutables(TrackingEventProcessor.class.getMethod("start"),
                                                                                        TrackingEventProcessor.class.getMethod("shutdownAsync"));
        });
        tryRegister(() -> {
            registry.reflection().forType(MessageHandlerRegistrar.class).withExecutables(MessageHandlerRegistrar.class.getMethod("start"),
                                                                                         MessageHandlerRegistrar.class.getMethod("shutdown"));
        });
        tryRegister(() -> {
            registry.reflection().forType(AxonServerCommandBus.class).withExecutables(AxonServerCommandBus.class.getDeclaredMethod("start"),
                                                                                      AxonServerCommandBus.class.getDeclaredMethod("shutdownDispatching"),
                                                                                      AxonServerCommandBus.class.getDeclaredMethod("disconnect"));
        });
        tryRegister(() -> {
            registry.reflection().forType(AxonServerConnectionManager.class).withExecutables(AxonServerConnectionManager.class.getDeclaredMethod("start"),
                                                                                             AxonServerConnectionManager.class.getDeclaredMethod("shutdown"));
        });
        tryRegister(() -> {
            registry.reflection().forType(AxonServerEventScheduler.class).withExecutables(AxonServerEventScheduler.class.getDeclaredMethod("start"),
                                                                                          AxonServerEventScheduler.class.getDeclaredMethod("shutdownDispatching"));
        });
        tryRegister(() -> {
            registry.reflection().forType(AxonServerQueryBus.class).withExecutables(AxonServerQueryBus.class.getDeclaredMethod("start"),
                                                                                    AxonServerQueryBus.class.getDeclaredMethod("shutdownDispatching"),
                                                                                    AxonServerQueryBus.class.getDeclaredMethod("disconnect"));
        });
        tryRegister(() -> {
            registry.reflection().forType(EventProcessorControlService.class).withExecutables(EventProcessorControlService.class.getDeclaredMethod("start"));
        });
    }

    private void tryRegister(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception | LinkageError e) {
            // probably a method or class not found. Ignore
        }
    }

    private void registerServiceLoaderImplementations(NativeConfigurationRegistry registry, Class<?> interfaceName) {
        Iterator<?> services = ServiceLoader.load(interfaceName).iterator();

        while (services.hasNext()) {
            try {
                Object next = services.next();
                registry.reflection().forType(next.getClass()).withAccess(TypeAccess.values());
            } catch (ServiceConfigurationError | NoClassDefFoundError e) {
                // ignore
            }
        }
    }

    private interface ThrowingRunnable {

        void run() throws Exception;
    }
}
