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

package org.axonframework.eventhandling.annotation;

import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.eventhandling.annotations.SequencingByProperty;
import org.axonframework.eventhandling.annotations.SequencingPolicy;
import org.axonframework.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.eventhandling.sequencing.SequentialPolicy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class for the {@link SequencingPolicyResolver} implementations and factory.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SequencingPolicyResolverTest {

    @Nested
    class DirectSequencingPolicyResolverTest {

        private final DirectSequencingPolicyResolver resolver = new DirectSequencingPolicyResolver();

        @Test
        void should_resolve_method_level_sequencing_policy() throws Exception {
            // given
            Method method = TestHandlers.class.getDeclaredMethod("methodWithSequencingPolicy", String.class);

            // when
            var result = resolver.resolve(method);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(SequentialPolicy.class);
        }

        @Test
        void should_resolve_class_level_sequencing_policy() throws Exception {
            // given
            Method method = ClassLevelSequencingPolicyHandler.class.getDeclaredMethod("handle", String.class);

            // when
            var result = resolver.resolve(method);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(SequentialPolicy.class);
        }

        @Test
        void should_prefer_method_over_class_level_annotation() throws Exception {
            // given
            Method method = ClassLevelSequencingPolicyHandler.class.getDeclaredMethod("methodOverride", String.class);

            // when
            var result = resolver.resolve(method);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(PropertySequencingPolicy.class);
        }

        @Test
        void should_return_empty_when_no_annotation_present() throws Exception {
            // given
            Method method = TestHandlers.class.getDeclaredMethod("methodWithoutAnnotation", String.class);

            // when
            var result = resolver.resolve(method);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class SequencingByPropertyResolverTest {

        private final SequencingByPropertyResolver resolver = new SequencingByPropertyResolver();

        @Test
        void should_resolve_method_level_sequencing_by_property() throws Exception {
            // given
            Method method = TestHandlers.class.getDeclaredMethod("methodWithSequencingByProperty", String.class);

            // when
            var result = resolver.resolve(method);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(PropertySequencingPolicy.class);
            assertThat(result.get().parameters()).containsExactly("testProperty");
        }

        @Test
        void should_resolve_class_level_sequencing_by_property() throws Exception {
            // given
            Method method = ClassLevelSequencingByPropertyHandler.class.getDeclaredMethod("handle", String.class);

            // when
            var result = resolver.resolve(method);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(PropertySequencingPolicy.class);
            assertThat(result.get().parameters()).containsExactly("classProperty");
        }

        @Test
        void should_return_empty_when_no_annotation_present() throws Exception {
            // given
            Method method = TestHandlers.class.getDeclaredMethod("methodWithoutAnnotation", String.class);

            // when
            var result = resolver.resolve(method);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class ChainedSequencingPolicyResolverTest {

        @Test
        void should_return_result_from_first_resolver_that_can_handle() throws Exception {
            // given
            var directResolver = new DirectSequencingPolicyResolver();
            var propertyResolver = new SequencingByPropertyResolver();
            var chainedResolver = new ChainedSequencingPolicyResolver(List.of(directResolver, propertyResolver));
            Method method = TestHandlers.class.getDeclaredMethod("methodWithSequencingPolicy", String.class);

            // when
            var result = chainedResolver.resolve(method);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().type()).isEqualTo(SequentialPolicy.class);
        }

        @Test
        void should_return_empty_when_no_resolver_can_handle() throws Exception {
            // given
            var directResolver = new DirectSequencingPolicyResolver();
            var propertyResolver = new SequencingByPropertyResolver();
            var chainedResolver = new ChainedSequencingPolicyResolver(List.of(directResolver, propertyResolver));
            Method method = TestHandlers.class.getDeclaredMethod("methodWithoutAnnotation", String.class);

            // when
            var result = chainedResolver.resolve(method);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void should_throw_exception_when_resolvers_list_is_null() {
            // when/then
            assertThatThrownBy(() -> new ChainedSequencingPolicyResolver(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Resolvers list must not be null");
        }

        @Test
        void should_throw_exception_when_resolvers_list_is_empty() {
            // when/then
            assertThatThrownBy(() -> new ChainedSequencingPolicyResolver(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Resolvers list must not be empty");
        }
    }

    @Nested
    class SequencingPolicyResolverFactoryTest {

        @Test
        void should_create_default_resolver() {
            // when
            var resolver = SequencingPolicyResolverFactory.createDefaultResolver();

            // then
            assertThat(resolver).isInstanceOf(ChainedSequencingPolicyResolver.class);
        }

        @Test
        void should_create_custom_resolver() {
            // given
            List<SequencingPolicyResolver> customResolvers = List.of(new DirectSequencingPolicyResolver());

            // when
            var resolver = SequencingPolicyResolverFactory.createCustomResolver(customResolvers);

            // then
            assertThat(resolver).isInstanceOf(ChainedSequencingPolicyResolver.class);
        }
    }

    // Test helper classes
    private static class TestHandlers {

        @EventHandler
        @SequencingPolicy(type = SequentialPolicy.class)
        void methodWithSequencingPolicy(String event) {}

        @EventHandler
        @SequencingByProperty("testProperty")
        void methodWithSequencingByProperty(String event) {}

        @EventHandler
        void methodWithoutAnnotation(String event) {}
    }

    @SequencingPolicy(type = SequentialPolicy.class)
    private static class ClassLevelSequencingPolicyHandler {

        @EventHandler
        void handle(String event) {}

        @EventHandler
        @SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"overrideProperty"})
        void methodOverride(String event) {}
    }

    @SequencingByProperty("classProperty")
    private static class ClassLevelSequencingByPropertyHandler {

        @EventHandler
        void handle(String event) {}
    }
}