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

import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.messaging.core.annotation.Namespace;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.config.BeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link EventHandlerSelector}.
 *
 * @author Steven van Beelen
 */
class EventHandlerSelectorTest {

    @Test
    void matchesNamespaceOnTypeMatchesForNamespaceOnBeanType() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("direct");
        EventProcessorDefinition.EventHandlerDescriptor descriptor = descriptorFor(DirectlyAnnotatedClass.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isTrue();
    }

    @Test
    void matchesNamespaceOnTypeMatchesForNamespaceOnEnclosingClassOfBeanType() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("container");
        EventProcessorDefinition.EventHandlerDescriptor descriptor =
                descriptorFor(AnnotatedContainer.NestedClass.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isTrue();
    }

    @Test
    void matchesNamespaceOnTypeMatchesForNamespaceOnOutermostEnclosingClassOfBeanType() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("container");
        EventProcessorDefinition.EventHandlerDescriptor descriptor =
                descriptorFor(AnnotatedContainer.NestedClass.DeeplyNestedClass.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isTrue();
    }

    @Test
    void matchesNamespaceOnTypePrefersNamespaceAnnotationOnInnerClassOverOuterClass() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("nested");
        EventProcessorDefinition.EventHandlerDescriptor descriptor =
                descriptorFor(AnnotatedContainer.AnnotatedNestedClass.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isTrue();
    }

    @Test
    void matchesNamespaceOnTypeDoesNotMatchWhenNamespaceDiffers() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("different-namespace");
        EventProcessorDefinition.EventHandlerDescriptor descriptor = descriptorFor(DirectlyAnnotatedClass.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isFalse();
    }

    @Test
    void matchesNamespaceOnTypeDoesNotMatchWhenNamespaceAnnotationIsNotPresent() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("any-namespace");
        EventProcessorDefinition.EventHandlerDescriptor descriptor = descriptorFor(UnannotatedClass.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isFalse();
    }

    @Test
    void matchesNamespaceOnTypeDoesNotMatchWhenBeanTypeIsNull() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("any-namespace");
        EventProcessorDefinition.EventHandlerDescriptor descriptor = descriptorFor(null);

        boolean result = selector.test(descriptor);

        assertThat(result).isFalse();
    }

    @Test
    void matchesNamespaceOnTypeContinuesSearchWhenNamespaceAnnotationHasEmptyValue() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("container");
        EventProcessorDefinition.EventHandlerDescriptor descriptor =
                descriptorFor(AnnotatedContainer.NestedWithEmptyAnnotation.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isTrue();
    }

    @Test
    void matchesNamespaceOnTypeDoesNotMatchWhenGivenNamespaceIsNull() {
        //noinspection DataFlowIssue
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType(null);
        EventProcessorDefinition.EventHandlerDescriptor descriptor = descriptorFor(UnannotatedClass.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isFalse();
    }

    @Test
    void matchesNamespaceOnTypeDoesNotMatchWhenGivenNamespaceIsEmpty() {
        EventHandlerSelector selector = EventHandlerSelector.matchesNamespaceOnType("");
        EventProcessorDefinition.EventHandlerDescriptor descriptor = descriptorFor(UnannotatedClass.class);

        boolean result = selector.test(descriptor);

        assertThat(result).isFalse();
    }

    private EventProcessorDefinition.EventHandlerDescriptor descriptorFor(@Nullable Class<?> beanType) {
        return new TestEventHandlerDescriptor(beanType);
    }

    @Namespace("direct")
    private static class DirectlyAnnotatedClass {

    }

    @Namespace("container")
    private static class AnnotatedContainer {

        private static class NestedClass {

            private static class DeeplyNestedClass {

            }
        }

        @Namespace("nested")
        private static class AnnotatedNestedClass {

        }

        @Namespace("")
        private static class NestedWithEmptyAnnotation {

        }
    }

    private static class UnannotatedClass {

    }

    /**
     * Simple test implementation of {@link EventProcessorDefinition.EventHandlerDescriptor}.
     */
    private record TestEventHandlerDescriptor(@Nullable Class<?> beanType)
            implements EventProcessorDefinition.EventHandlerDescriptor {

        @NonNull
        @Override
        public String beanName() {
            return beanType != null ? beanType.getSimpleName() : "unknown";
        }

        @NonNull
        @Override
        public BeanDefinition beanDefinition() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @NonNull
        @Override
        public Object resolveBean() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @NonNull
        @Override
        public ComponentBuilder<Object> component() {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }
}
