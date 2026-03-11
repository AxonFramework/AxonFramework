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

import org.axonframework.common.StringUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.core.annotation.Namespace;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Functional interface describing a filter for the
 * {@link org.axonframework.extension.spring.config.EventProcessorDefinition.EventHandlerDescriptor}.
 * <p>
 * Used as part of the {@link EventProcessorDefinition.SelectorStep} to allow you to form a predicate to assign
 * {@link org.axonframework.messaging.eventhandling.EventHandlingComponent} beans to an
 * {@link org.axonframework.messaging.eventhandling.processing.EventProcessor}
 *
 * @author Steven van Beelen
 * @since 5.1.0
 */
@FunctionalInterface
public interface EventHandlerSelector extends Predicate<EventProcessorDefinition.EventHandlerDescriptor> {

    /**
     * Implementation of the {@code EventHandlerSelector} that matches
     * {@link EventProcessorDefinition.EventHandlerDescriptor EventHandlerDescriptors} for which the given
     * {@code namespace} matches with the value of the {@link Namespace} annotation.
     * <p>
     * The {@code Namespace} is searched for on several levels in the following order:
     * <ol>
     *     <li>On the {@link EventProcessorDefinition.EventHandlerDescriptor#beanType()}</li>
     *     <li>The {@link Class#getEnclosingClass() enclosing classes} (from innermost to outermost) of the {@link EventProcessorDefinition.EventHandlerDescriptor#beanType()}</li>
     *     <li>The {@link Class#getPackage() package} of the {@link EventProcessorDefinition.EventHandlerDescriptor#beanType()}</li>
     *     <li>The {@link Class#getModule() module} of the {@link EventProcessorDefinition.EventHandlerDescriptor#beanType()}</li>
     * </ol>
     *
     * @param namespace the namespace to match with the {@code value} of the {@link Namespace}
     * @return an {@code EventHandlerSelector} that matches the given {@code namespace} with the {@code value} of the
     * {@link Namespace}
     */
    static EventHandlerSelector matchesNamespaceOnType(String namespace) {
        return eventHandlerDescriptor -> {
            if (StringUtils.emptyOrNull(namespace)) {
                // A null or empty namespace cannot be matched with
                return false;
            }
            Class<?> type = eventHandlerDescriptor.beanType();
            if (type == null) {
                // Spring couldn't uncover the type, so we can't uncover if there's a Namespace present
                return false;
            }
            String uncoveredNamespace =
                    AnnotationUtils.findAnnotationAttributesOnType(
                                           type,
                                           Namespace.class,
                                           attrs -> !StringUtils.emptyOrNull((String) attrs.get("namespace"))
                                   )
                                   .map(attrs -> attrs.get("namespace"))
                                   .map(value -> (String) value)
                                   .orElse("");
            return Objects.equals(namespace, uncoveredNamespace);
        };
    }
}
