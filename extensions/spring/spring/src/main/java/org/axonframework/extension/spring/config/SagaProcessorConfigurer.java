/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.spring.config;

import org.axonframework.common.annotation.Internal;
import org.axonframework.common.annotation.RegistrationScope;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.extension.spring.stereotype.Saga;
import org.axonframework.messaging.eventhandling.configuration.EventHandlingComponentsConfigurer;
import org.axonframework.messaging.eventhandling.configuration.EventProcessorModule;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link ConfigurationEnhancer} that builds one dedicated {@link EventProcessorModule} per {@link Saga @Saga} bean.
 * <p>
 * A Saga is never grouped onto a processor with another Saga or with a regular event handling component: Axon
 * Framework 4 offered that grouping only as a thread-sharing workaround from an era with a single, per-processor
 * threading event processor implementation, not as a feature applications relied on for its own sake. An application
 * that wants several Saga processors to share a thread pool can still do so today, by giving each of them the same
 * {@code Executor} through a matching {@link EventProcessorDefinition}. Keeping this wiring entirely separate from
 * {@link DefaultProcessorModuleFactory} keeps that shared, regular-handler pipeline free of Saga-specific concerns.
 * <p>
 * Resolves each Saga's processor name from a {@link org.axonframework.messaging.core.annotation.Namespace} on its
 * type, falling back to {@link SpringSagaDescriptor#preferredProcessorName()}. A matching {@link EventProcessorDefinition}
 * (by that name) may still override the processor's mode and settings, e.g. to replay from the start of the stream or
 * to assign a shared {@code Executor} -- but, unlike a regular handler, a Saga can never be selected into a processor
 * by such a definition's selector: it does not participate in that shared assignment mechanism at all.
 * <p>
 * Registered as a bean by {@code SagaAutoConfiguration}; an application never creates this itself.
 * <p>
 * Not copied into a module's own nested {@link ComponentRegistry}: a {@link org.axonframework.common.configuration.Module}
 * is built through the same {@link ConfigurationEnhancer} invocation this class registers modules from, so copying it
 * down would re-run this enhancer for every processor module it builds, which builds another full set of processor
 * modules on that module's own registry, which builds another set on each of those, without ever terminating.
 *
 * @author Mateusz Nowak
 * @since 5.4.0
 */
@Internal
@RegistrationScope("Don't copy this enhancer, or building one Saga's processor module recurses into building "
        + "another full set of Saga processor modules on that module's own registry, forever.")
public class SagaProcessorConfigurer implements ConfigurationEnhancer, ApplicationContextAware {

    private @Nullable ApplicationContext applicationContext;

    @Override
    public void enhance(ComponentRegistry registry) {
        var context = Objects.requireNonNull(applicationContext);
        Map<String, SpringSagaDescriptor> sagas = context.getBeansOfType(SpringSagaDescriptor.class);
        if (sagas.isEmpty()) {
            return;
        }
        List<EventProcessorDefinition> definitions = context.getBeanProvider(EventProcessorDefinition.class)
                                                             .orderedStream()
                                                             .toList();
        Map<String, EventProcessorSettings> settingsMap =
                context.getBean(EventProcessorSettings.MapWrapper.class).settings();

        for (SpringSagaDescriptor saga : sagas.values()) {
            String processorName = EventProcessorModuleAssembler.resolveNamespace(saga.beanType())
                                                                 .or(saga::preferredProcessorName)
                                                                 .orElseThrow();
            var settings = Optional.ofNullable(settingsMap.get(processorName))
                                   .orElseGet(() -> settingsMap.get(EventProcessorSettings.DEFAULT));
            Function<EventHandlingComponentsConfigurer.RequiredComponentPhase, EventHandlingComponentsConfigurer.CompletePhase>
                    componentRegistration = phase ->
                    (EventHandlingComponentsConfigurer.CompletePhase) phase.declarative(
                            saga.beanName(), saga.handlingComponent()
                    );
            registry.registerModule(EventProcessorModuleAssembler.assemble(
                    processorName,
                    settings,
                    definitions,
                    List.of(), // no DLQ for Sagas -- Axon Framework 4 never supported dead-lettering for them
                    saga.pooledStreamingDefaults(),
                    componentRegistration
            ));
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
