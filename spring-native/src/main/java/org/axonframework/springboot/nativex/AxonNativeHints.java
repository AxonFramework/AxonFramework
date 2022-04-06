/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.springboot.nativex;

import com.fasterxml.jackson.databind.ser.std.ClassSerializer;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.RoutingKey;
import org.axonframework.commandhandling.gateway.Timeout;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.Priority;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.AllowReplay;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.MergedTrackingToken;
import org.axonframework.eventhandling.MultiSourceTrackingToken;
import org.axonframework.eventhandling.ReplayToken;
import org.axonframework.eventhandling.tokenstore.ConfigToken;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.HasHandlerAttributes;
import org.axonframework.messaging.annotation.MessageHandler;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.OptionalResponseType;
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
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeProxyEntry;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeResourcesEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.nativex.hint.TypeAccess;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * A {@link BeanFactoryNativeConfigurationProcessor} implementation registering specifics for Axon Framework
 * applications.
 * <p>
 * This component registers Axon's {@link ServiceLoader} usages, the annotations meta-annotated with {@link
 * HasHandlerAttributes}, classes that the framework serializes, and some resources and proxies.
 * <p>
 * Note that this service is part of Axon's <b>experimental</b> release.
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class AxonNativeHints implements BeanFactoryNativeConfigurationProcessor {

    @Override
    public void process(ConfigurableListableBeanFactory beanFactory, NativeConfigurationRegistry registry) {
        NativeConfigurationRegistry.ReflectionConfiguration reflection = registry.reflection();

        // Register interfaces that use the Service Loader mechanism.
        registerServiceLoaderImplementations(reflection, HandlerDefinition.class);
        registerServiceLoaderImplementations(reflection, HandlerEnhancerDefinition.class);
        registerServiceLoaderImplementations(reflection, ParameterResolverFactory.class);
        registerServiceLoaderImplementations(reflection, ContentTypeConverter.class);
        registerServiceLoaderImplementations(reflection, PropertyAccessStrategy.class);
        registerServiceLoaderImplementations(reflection, IdentifierFactory.class);

        // Register reflective use on all Axon's annotations meta-annotated with HasHandlerAttributes.
        reflection.forType(Priority.class).withAccess(TypeAccess.values());
        reflection.forType(AggregateRoot.class).withAccess(TypeAccess.values());
        reflection.forType(CommandHandler.class).withAccess(TypeAccess.values());
        reflection.forType(MessageHandler.class).withAccess(TypeAccess.values());
        reflection.forType(Qualifier.class).withAccess(TypeAccess.values());
        reflection.forType(QueryHandler.class).withAccess(TypeAccess.values());
        reflection.forType(EventHandler.class).withAccess(TypeAccess.values());
        reflection.forType(EventSourcingHandler.class).withAccess(TypeAccess.values());
        reflection.forType(ProcessingGroup.class).withAccess(TypeAccess.values());
        reflection.forType(StartHandler.class).withAccess(TypeAccess.values());
        reflection.forType(ShutdownHandler.class).withAccess(TypeAccess.values());
        reflection.forType(RoutingKey.class).withAccess(TypeAccess.values());
        reflection.forType(Timeout.class).withAccess(TypeAccess.values());
        reflection.forType(MetaDataValue.class).withAccess(TypeAccess.values());
        reflection.forType(AllowReplay.class).withAccess(TypeAccess.values());
        reflection.forType(AggregateMember.class).withAccess(TypeAccess.values());
        reflection.forType(EntityId.class).withAccess(TypeAccess.values());
        reflection.forType(AggregateVersion.class).withAccess(TypeAccess.values());
        reflection.forType(AggregateIdentifier.class).withAccess(TypeAccess.values());
        reflection.forType(TargetAggregateIdentifier.class).withAccess(TypeAccess.values());
        reflection.forType(DeadlineHandler.class).withAccess(TypeAccess.values());
        reflection.forType(HasHandlerAttributes.class).withAccess(TypeAccess.values());

        // Register reflective use on concrete classes Axon de-/serializes.
        reflection.forType(SagaEntry.class)
                  .withAccess(TypeAccess.values());
        reflection.forType(AbstractSagaEntry.class)
                  .withAccess(TypeAccess.values());
        reflection.forType(GlobalSequenceTrackingToken.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        reflection.forType(GapAwareTrackingToken.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        reflection.forType(MergedTrackingToken.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        reflection.forType(MultiSourceTrackingToken.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        reflection.forType(ReplayToken.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        reflection.forType(ConfigToken.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        reflection.forType(MultipleInstancesResponseType.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        reflection.forType(OptionalResponseType.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        reflection.forType(InstanceResponseType.class)
                  .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        // We need this registration from Jackson because we serializer the type from time to time.
        // Spring Native doesn't register this itself, so we do it for it.
        try {
            reflection.forType(ClassSerializer.class)
                      .withAccess(TypeAccess.PUBLIC_METHODS, TypeAccess.PUBLIC_CONSTRUCTORS);
        } catch (Exception | LinkageError e) {
            // Probably a method or class not found. Ignore this exception or error.
        }

        // Register resources.
        registry.resources()
                .add(NativeResourcesEntry.of("axonserver_download.txt"))
                .add(NativeResourcesEntry.of("org/axonframework/eventsourcing/eventstore/jpa/SQLErrorCode.properties"));
        // Register proxies.
        registry.proxy()
                .add(NativeProxyEntry.ofInterfaceNames(
                        "java.sql.Connection",
                        "org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper$UoWAttachedConnection"
                ));
    }

    private void registerServiceLoaderImplementations(NativeConfigurationRegistry.ReflectionConfiguration reflection,
                                                      Class<?> interfaceName) {
        Iterator<?> services = ServiceLoader.load(interfaceName).iterator();

        while (services.hasNext()) {
            try {
                Object next = services.next();
                reflection.forType(next.getClass()).withAccess(TypeAccess.values());
            } catch (ServiceConfigurationError | NoClassDefFoundError e) {
                // Ignore these errors, as they shouldn't block application start-up.
            }
        }
    }
}
