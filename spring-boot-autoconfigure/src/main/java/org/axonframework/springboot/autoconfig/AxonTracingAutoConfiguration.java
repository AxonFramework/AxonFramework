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

package org.axonframework.springboot.autoconfig;

import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanAttributesProvider;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.attributes.AggregateIdentifierSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageIdSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageNameSpanAttributesProvider;
import org.axonframework.tracing.attributes.MessageTypeSpanAttributesProvider;
import org.axonframework.tracing.attributes.MetadataSpanAttributesProvider;
import org.axonframework.tracing.attributes.PayloadTypeSpanAttributesProvider;
import org.axonframework.tracing.opentelemetry.OpenTelemetrySpanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Configures tracing for Axon Framework. Defaults to the {@link NoOpSpanFactory}. If the
 * {@link OpenTelemetrySpanFactory} is on the classpath, uses Open Telemetry instead.
 * <p>
 * For Open tracing, take a look at the <a href="https://github.com/AxonFramework/extension-tracing">Open Tracing
 * extension</a>
 */
@Configuration
@AutoConfigureBefore({AxonServerAutoConfiguration.class, AxonAutoConfiguration.class})
public class AxonTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingClass("org.axonframework.tracing.opentelemetry.OpenTelemetrySpanFactory")
    @ConditionalOnMissingBean(SpanFactory.class)
    public SpanFactory noOpSpanFactory() {
        return NoOpSpanFactory.INSTANCE;
    }

    @Bean
    @ConditionalOnClass(name = "org.axonframework.tracing.opentelemetry.OpenTelemetrySpanFactory")
    @ConditionalOnMissingBean(SpanFactory.class)
    public SpanFactory openTelemetrySpanFactory(List<SpanAttributesProvider> attributesProviders) {
        return new OpenTelemetrySpanFactory(attributesProviders, false);
    }

    @Bean
    public List<SpanAttributesProvider> spanAttributesProviders() {
        return Arrays.asList(
                new AggregateIdentifierSpanAttributesProvider(),
                new MessageIdSpanAttributesProvider(),
                new MessageNameSpanAttributesProvider(),
                new MessageTypeSpanAttributesProvider(),
                new MetadataSpanAttributesProvider(),
                new PayloadTypeSpanAttributesProvider()
        );
    }
}
