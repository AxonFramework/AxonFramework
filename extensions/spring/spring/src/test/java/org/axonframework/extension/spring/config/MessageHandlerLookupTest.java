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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

class MessageHandlerLookupTest {

    private static final String EVENT_CONFIGURER = "MessageHandlerConfigurer$$Axon$$EVENT";

    @Nested
    class EventConfigurerRegistration {

        @Test
        void registersWhenAnotherLookupContributedAnEventHandlerDescriptor() {
            // given
            DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
            beanFactory.registerBeanDefinition(
                    "eventDescriptor",
                    BeanDefinitionBuilder.genericBeanDefinition(DescriptorStub.class).getBeanDefinition()
            );

            // when
            new MessageHandlerLookup().postProcessBeanFactory(beanFactory);

            // then
            assertThat(beanFactory.containsBeanDefinition(EVENT_CONFIGURER)).isTrue();
        }

        @Test
        void doesNotRegisterWhenThereAreNoEventHandlersToConfigure() {
            // given
            DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

            // when
            new MessageHandlerLookup().postProcessBeanFactory(beanFactory);

            // then
            assertThat(beanFactory.containsBeanDefinition(EVENT_CONFIGURER)).isFalse();
        }
    }

    static class DescriptorStub implements EventProcessorDefinition.EventHandlerDescriptor {

        @Override
        public String beanName() {
            return "event";
        }

        @Override
        public BeanDefinition beanDefinition() {
            return BeanDefinitionBuilder.genericBeanDefinition(Object.class).getBeanDefinition();
        }

        @Override
        public Class<?> beanType() {
            return Object.class;
        }

        @Override
        public Object resolveBean() {
            return new Object();
        }

        @Override
        public ComponentBuilder<Object> component() {
            return configuration -> resolveBean();
        }
    }
}
