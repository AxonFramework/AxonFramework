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

package org.axonframework.springboot;

import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.springboot.autoconfig.AxonServerActuatorAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration;
import org.axonframework.springboot.autoconfig.OpenTelemetryAutoConfiguration;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanAttributesProvider;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TracingHandlerEnhancerDefinition;
import org.axonframework.tracing.attributes.AggregateIdentifierSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageIdSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageNameSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageTypeSpanAttributesProvider;
import org.axonframework.tracing.attributes.MetadataSpanAttributesProvider;
import org.axonframework.tracing.attributes.PayloadTypeSpanAttributesProvider;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class AxonAutoConfigurationWithTracingTest {

    @Test
    void spanFactoryDefaultsToNoop() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertTrue(context.containsBean("spanFactory"));
                    assertNotNull(context.getBean(SpanFactory.class));
                    assertEquals(NoOpSpanFactory.class, context.getBean(SpanFactory.class).getClass());
                });
    }


    @Test
    void handlerEnhancerDefinitionIsRegistered() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertTrue(context.containsBean("tracingHandlerEnhancerDefinition"));
                    assertNotNull(context.getBean(HandlerEnhancerDefinition.class));
                    assertEquals(TracingHandlerEnhancerDefinition.class,
                                 context.getBean(HandlerEnhancerDefinition.class).getClass());
                });
    }


    @Test
    void registersAllAttrributeProvidersByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertTrue(context.containsBean("aggregateIdentifierSpanAttributesProvider"));
                    assertNotNull(context.getBean(AggregateIdentifierSpanAttributesProvider.class));

                    assertTrue(context.containsBean("messageIdSpanAttributesProvider"));
                    assertNotNull(context.getBean(MessageIdSpanAttributesProvider.class));

                    assertTrue(context.containsBean("messageNameSpanAttributesProvider"));
                    assertNotNull(context.getBean(MessageNameSpanAttributesProvider.class));

                    assertTrue(context.containsBean("messageTypeSpanAttributesProvider"));
                    assertNotNull(context.getBean(MessageTypeSpanAttributesProvider.class));

                    assertTrue(context.containsBean("metadataSpanAttributesProvider"));
                    assertNotNull(context.getBean(MetadataSpanAttributesProvider.class));

                    assertTrue(context.containsBean("payloadTypeSpanAttributesProvider"));
                    assertNotNull(context.getBean(AggregateIdentifierSpanAttributesProvider.class));
                });
    }

    @Test
    void registersAllAttrributeProvidersByDefaultAsList() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    Map<String, SpanAttributesProvider> beansOfType = context.getBeansOfType(SpanAttributesProvider.class);

                    assertTrue(beansOfType.containsKey("aggregateIdentifierSpanAttributesProvider"));
                    assertTrue(beansOfType.containsKey("messageIdSpanAttributesProvider"));
                    assertTrue(beansOfType.containsKey("messageNameSpanAttributesProvider"));
                    assertTrue(beansOfType.containsKey("messageTypeSpanAttributesProvider"));
                    assertTrue(beansOfType.containsKey("metadataSpanAttributesProvider"));
                    assertTrue(beansOfType.containsKey("payloadTypeSpanAttributesProvider"));
                });
    }

    @Test
    void aggregateIdAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "axon.tracing.attribute-providers.aggregate-identifier=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertFalse(context.containsBean("aggregateIdentifierSpanAttributesProvider"));
                    assertThrows(NoSuchBeanDefinitionException.class,
                                 () -> context.getBean(AggregateIdentifierSpanAttributesProvider.class));
                });
    }


    @Test
    void messageIdAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "axon.tracing.attribute-providers.message-id=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertFalse(context.containsBean("messageIdSpanAttributesProvider"));
                    assertThrows(NoSuchBeanDefinitionException.class,
                                 () -> context.getBean(MessageIdSpanAttributesProvider.class));
                });
    }

    @Test
    void messageNameAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "axon.tracing.attribute-providers.message-name=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertFalse(context.containsBean("messageNameSpanAttributesProvider"));
                    assertThrows(NoSuchBeanDefinitionException.class,
                                 () -> context.getBean(MessageNameSpanAttributesProvider.class));
                });
    }

    @Test
    void messageTypeAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "axon.tracing.attribute-providers.message-type=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertFalse(context.containsBean("messageTypeSpanAttributesProvider"));
                    assertThrows(NoSuchBeanDefinitionException.class,
                                 () -> context.getBean(MessageTypeSpanAttributesProvider.class));
                });
    }

    @Test
    void metadataAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "axon.tracing.attribute-providers.metadata=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertFalse(context.containsBean("metadataSpanAttributesProvider"));
                    assertThrows(NoSuchBeanDefinitionException.class,
                                 () -> context.getBean(MetadataSpanAttributesProvider.class));
                });
    }

    @Test
    void payloadTypeAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "axon.tracing.attribute-providers.payload-type=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertNotNull(context);

                    assertFalse(context.containsBean("payloadTypeSpanAttributesProvider"));
                    assertThrows(NoSuchBeanDefinitionException.class,
                                 () -> context.getBean(PayloadTypeSpanAttributesProvider.class));
                });
    }

    @Test
    void setsSpanAttributeProviderOnTracer() {
        SpanFactory spanFactory = Mockito.mock(SpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(SpanFactory.class, () -> spanFactory)
                .run(context -> {
                    assertNotNull(context);
                    assertSame(spanFactory, context.getBean(SpanFactory.class));

                    int numberOfProviders = context.getBeansOfType(SpanAttributesProvider.class).size();
                    Mockito.verify(spanFactory, Mockito.times(numberOfProviders)).registerSpanAttributeProvider(any());
                });
    }

    @EnableAutoConfiguration(exclude = {
            AxonServerAutoConfiguration.class,
            AxonServerBusAutoConfiguration.class,
            AxonServerActuatorAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JmxAutoConfiguration.class,
            OpenTelemetryAutoConfiguration.class,
            WebClientAutoConfiguration.class,
    })
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    @Configuration
    public static class Context {

    }
}
