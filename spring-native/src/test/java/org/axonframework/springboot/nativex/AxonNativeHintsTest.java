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
import org.junit.jupiter.api.*;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.DefaultNativeReflectionEntry;
import org.springframework.aot.context.bootstrap.generator.infrastructure.nativex.NativeConfigurationRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AxonNativeHints}.
 *
 * @author Steven van Beelen
 */
class AxonNativeHintsTest {

    private final AxonNativeHints testSubject = new AxonNativeHints();

    @Test
    void testProcessRegisteredNativeResourceFromAxon() {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        NativeConfigurationRegistry registry = new NativeConfigurationRegistry();

        testSubject.process(beanFactory, registry);

        List<Class<?>> registeredTypes = registry.reflection()
                                                 .reflectionEntries()
                                                 .map(DefaultNativeReflectionEntry::getType)
                                                 .collect(Collectors.toList());

        assertServiceLoaderRegisteredType(registeredTypes, HandlerDefinition.class);
        assertServiceLoaderRegisteredType(registeredTypes, HandlerEnhancerDefinition.class);
        assertServiceLoaderRegisteredType(registeredTypes, ParameterResolverFactory.class);
        assertServiceLoaderRegisteredType(registeredTypes, ContentTypeConverter.class);
        assertServiceLoaderRegisteredType(registeredTypes, PropertyAccessStrategy.class);
        assertServiceLoaderRegisteredType(registeredTypes, IdentifierFactory.class);
        assertTrue(registeredTypes.contains(Priority.class));
        assertTrue(registeredTypes.contains(AggregateRoot.class));
        assertTrue(registeredTypes.contains(CommandHandler.class));
        assertTrue(registeredTypes.contains(MessageHandler.class));
        assertTrue(registeredTypes.contains(Qualifier.class));
        assertTrue(registeredTypes.contains(QueryHandler.class));
        assertTrue(registeredTypes.contains(EventHandler.class));
        assertTrue(registeredTypes.contains(EventSourcingHandler.class));
        assertTrue(registeredTypes.contains(ProcessingGroup.class));
        assertTrue(registeredTypes.contains(StartHandler.class));
        assertTrue(registeredTypes.contains(ShutdownHandler.class));
        assertTrue(registeredTypes.contains(RoutingKey.class));
        assertTrue(registeredTypes.contains(Timeout.class));
        assertTrue(registeredTypes.contains(MetaDataValue.class));
        assertTrue(registeredTypes.contains(AllowReplay.class));
        assertTrue(registeredTypes.contains(AggregateMember.class));
        assertTrue(registeredTypes.contains(EntityId.class));
        assertTrue(registeredTypes.contains(AggregateVersion.class));
        assertTrue(registeredTypes.contains(AggregateIdentifier.class));
        assertTrue(registeredTypes.contains(TargetAggregateIdentifier.class));
        assertTrue(registeredTypes.contains(DeadlineHandler.class));
        assertTrue(registeredTypes.contains(HasHandlerAttributes.class));
        assertTrue(registeredTypes.contains(SagaEntry.class));
        assertTrue(registeredTypes.contains(AbstractSagaEntry.class));
        assertTrue(registeredTypes.contains(GlobalSequenceTrackingToken.class));
        assertTrue(registeredTypes.contains(GapAwareTrackingToken.class));
        assertTrue(registeredTypes.contains(MergedTrackingToken.class));
        assertTrue(registeredTypes.contains(MultiSourceTrackingToken.class));
        assertTrue(registeredTypes.contains(ReplayToken.class));
        assertTrue(registeredTypes.contains(ConfigToken.class));
        assertTrue(registeredTypes.contains(MultipleInstancesResponseType.class));
        assertTrue(registeredTypes.contains(OptionalResponseType.class));
        assertTrue(registeredTypes.contains(InstanceResponseType.class));
        assertTrue(registeredTypes.contains(ClassSerializer.class));

        assertFalse(registry.resources().toResourcesDescriptor().isEmpty());
        assertFalse(registry.proxy().getEntries().isEmpty());

        verifyNoInteractions(beanFactory);
    }

    private void assertServiceLoaderRegisteredType(List<Class<?>> registeredTypes, Class<?> type) {
        Iterator<?> services = ServiceLoader.load(type).iterator();
        while (services.hasNext()) {
            try {
                Object next = services.next();
                assertTrue(registeredTypes.contains(next.getClass()));
            } catch (ServiceConfigurationError | NoClassDefFoundError e) {
                // Ignore these errors, as they shouldn't block application start-up.
            }
        }
    }
}