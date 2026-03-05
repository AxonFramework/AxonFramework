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

import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.OrderUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MessageHandlerLookup}, specifically verifying that {@code sortByOrder}
 * produces a stable, deterministic order for beans with equal {@code @Order} values.
 * <p>
 * Before the fix, {@code sortByOrder} went through a {@code HashMap} intermediate step, making
 * the tiebreaking order for equal-priority beans non-deterministic. The fix sorts the stream
 * directly with a natural-order (bean-name alphabetical) tiebreaker.
 */
class MessageHandlerLookupTest {

    private ConfigurableListableBeanFactory beanFactory;
    private BeanDefinitionRegistry registry;
    private MessageHandlerLookup lookup;

    @BeforeEach
    void setup() {
        // given: a beanFactory that is also a BeanDefinitionRegistry so we can capture registrations
        beanFactory = mock(ConfigurableListableBeanFactory.class,
                           withSettings().extraInterfaces(BeanDefinitionRegistry.class));
        registry = (BeanDefinitionRegistry) beanFactory;
        lookup = new MessageHandlerLookup();
    }

    @Nested
    class WhenSortingBeansWithEqualOrderValues {

        /**
         * The three bean names "ha", "hb", "hp" are specifically chosen so that Java HashMap
         * (capacity=16) iterates them in bucket order hp (bucket 8) → ha (bucket 9) → hb (bucket 10),
         * which is the OPPOSITE of alphabetical order ha < hb < hp.
         * <p>
         * The old {@code sortByOrder} implementation collected into a {@code HashMap} and then
         * called {@code sorted(comparingByValue())} — a stable sort. For equal values (all beans at
         * LOWEST_PRECEDENCE), a stable sort preserves HashMap iteration order, producing hp, ha, hb.
         * The fix sorts the stream directly with a natural-order tiebreaker, always producing ha, hb, hp.
         */
        @Test
        void beansWithEqualOrderAreSortedAlphabeticallyByBeanName() {
            // given: beans in insertion order ha, hp, hb — HashMap bucket order is hp, ha, hb
            when(beanFactory.getBeanDefinitionNames())
                    .thenReturn(new String[]{"ha", "hp", "hb"});
            stubSingletonBean("ha", AHandler.class);
            stubSingletonBean("hp", AHandler.class);
            stubSingletonBean("hb", AHandler.class);

            // when: postProcessBeanFactory discovers and sorts handlers
            lookup.postProcessBeanFactory(beanFactory);

            // then: the MessageHandlerConfigurer is registered with alphabetically sorted bean refs
            // "ha" < "hb" < "hp" alphabetically — the natural-order tiebreaker guarantees this
            var bdCaptor = ArgumentCaptor.forClass(BeanDefinition.class);
            verify(registry).registerBeanDefinition(eq("MessageHandlerConfigurer$$Axon$$EVENT"), bdCaptor.capture());

            @SuppressWarnings("unchecked")
            List<String> sortedBeanRefs = (List<String>)
                    bdCaptor.getValue().getConstructorArgumentValues()
                            .getArgumentValue(1, List.class).getValue();

            // Before the fix: HashMap bucket order produces ["hp", "ha", "hb"] — NOT alphabetical
            // After the fix: direct sort with natural-order tiebreaker produces ["ha", "hb", "hp"]
            assertThat(sortedBeanRefs).containsExactly("ha", "hb", "hp");
        }

        @Test
        void beansWithExplicitOrderAreOrderedByAnnotationValueFirst() {
            // given: beans with explicit @Order values (low = high priority)
            when(beanFactory.getBeanDefinitionNames())
                    .thenReturn(new String[]{"z-handler", "a-handler", "m-handler"});
            stubSingletonBean("z-handler", HandlerOrder1.class);      // @Order(1)
            stubSingletonBean("a-handler", AHandler.class);           // no @Order
            stubSingletonBean("m-handler", HandlerOrder2.class);      // @Order(2)

            // when
            lookup.postProcessBeanFactory(beanFactory);

            // then: @Order(1) first, @Order(2) second, no-@Order (LOWEST_PRECEDENCE) last
            var bdCaptor = ArgumentCaptor.forClass(BeanDefinition.class);
            verify(registry).registerBeanDefinition(eq("MessageHandlerConfigurer$$Axon$$EVENT"), bdCaptor.capture());

            @SuppressWarnings("unchecked")
            List<String> sortedBeanRefs = (List<String>)
                    bdCaptor.getValue().getConstructorArgumentValues()
                            .getArgumentValue(1, List.class).getValue();

            assertThat(sortedBeanRefs).containsExactly("z-handler", "m-handler", "a-handler");
        }

        @Test
        void beansWithSameExplicitOrderUseBeanNameAsTiebreaker() {
            // given: two beans with the same explicit @Order(5) — alphabetical tiebreaker needed
            when(beanFactory.getBeanDefinitionNames())
                    .thenReturn(new String[]{"z-handler", "a-handler"});
            stubSingletonBean("z-handler", HandlerZOrder5.class);  // @Order(5)
            stubSingletonBean("a-handler", HandlerAOrder5.class);  // @Order(5)

            // when
            lookup.postProcessBeanFactory(beanFactory);

            // then: alphabetical tiebreaker: "a-handler" before "z-handler"
            var bdCaptor = ArgumentCaptor.forClass(BeanDefinition.class);
            verify(registry).registerBeanDefinition(eq("MessageHandlerConfigurer$$Axon$$EVENT"), bdCaptor.capture());

            @SuppressWarnings("unchecked")
            List<String> sortedBeanRefs = (List<String>)
                    bdCaptor.getValue().getConstructorArgumentValues()
                            .getArgumentValue(1, List.class).getValue();

            assertThat(sortedBeanRefs).containsExactly("a-handler", "z-handler");
        }

        private void stubSingletonBean(String beanName, Class<?> beanType) {
            var bd = new GenericBeanDefinition();
            bd.setBeanClass(beanType);
            bd.setScope(BeanDefinition.SCOPE_SINGLETON);
            bd.setAbstract(false);
            bd.setAutowireCandidate(true);
            when(beanFactory.getBeanDefinition(beanName)).thenReturn(bd);
            //noinspection unchecked,rawtypes
            when(beanFactory.getType(beanName)).thenReturn((Class) beanType);
            when(beanFactory.containsBeanDefinition(startsWith("MessageHandlerConfigurer"))).thenReturn(false);
        }
    }

    /**
     * Documents exactly why "ha", "hp", "hb" are the adversarial names used in ordering tests.
     * <p>
     * This is not a test of production code — it proves that the OLD buggy {@code sortByOrder}
     * implementation (which went through a {@code HashMap} intermediate) would produce a DIFFERENT
     * order from alphabetical for these names, whereas the new implementation always produces
     * alphabetical order via the natural-order tiebreaker.
     */
    @Nested
    class DocumentingWhyAdversarialNamesWereChosen {

        @Test
        void hashMapIteratesHpHaHbNotAlphabeticalHaHbHp() {
            // given: the old buggy sortByOrder code, simulated inline
            List<String> found = List.of("ha", "hp", "hb");
            Map<String, Integer> orderMap = found.stream()
                                                 .collect(Collectors.toMap(
                                                         name -> name,
                                                         name -> Ordered.LOWEST_PRECEDENCE // all equal
                                                 ));
            // Streaming a HashMap's entrySet produces bucket-order, NOT insertion/alphabetical order.
            // For these 3 names, bucket indices are: "hp"→8, "ha"→9, "hb"→10.
            // So bucket iteration order is: hp, ha, hb — which is NOT alphabetical ha, hb, hp.
            List<String> buggyResult = orderMap.entrySet().stream()
                                               .sorted(Map.Entry.comparingByValue()) // stable; all equal → HashMap order
                                               .map(Map.Entry::getKey)
                                               .toList();

            // then: the buggy output is ha, hb, hp alphabetical... wait let me verify exactly
            // Actually HashMap iteration order for bucket 8→9→10 gives: hp, ha, hb
            assertThat(buggyResult).doesNotContainSequence("ha", "hb", "hp"); // NOT alphabetical
        }
    }

    // --- Stub handler classes ---

    static class AHandler {
        @EventHandler
        public void on(Object event) {}
    }

    @Order(1)
    static class HandlerOrder1 {
        @EventHandler
        public void on(Object event) {}
    }

    @Order(2)
    static class HandlerOrder2 {
        @EventHandler
        public void on(Object event) {}
    }

    @Order(5)
    static class HandlerAOrder5 {
        @EventHandler
        public void on(Object event) {}
    }

    @Order(5)
    static class HandlerZOrder5 {
        @EventHandler
        public void on(Object event) {}
    }
}
