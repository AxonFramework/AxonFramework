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

import org.axonframework.tracing.AxonSpanFactory;
import org.axonframework.tracing.SpanAttributesProvider;
import org.axonframework.tracing.opentelemetry.OpenTelemetryAxonSpanFactory;
import org.axonframework.tracing.tags.AggregateIdentifierSpanAttributesProvider;
import org.axonframework.tracing.tags.MessageIdSpanAttributesProvider;
import org.axonframework.tracing.tags.MessageNameSpanAttributesProvider;
import org.axonframework.tracing.tags.MessageTypeSpanAttributesProvider;
import org.axonframework.tracing.tags.MetadataSpanAttributesProvider;
import org.axonframework.tracing.tags.PayloadTypeSpanAttributesProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@AutoConfigureBefore({AxonServerAutoConfiguration.class, AxonAutoConfiguration.class})
public class AxonTracingAutoConfiguration {

    @Bean
    @ConditionalOnClass(name = "org.axonframework.tracing.opentelemetry.OpenTelemetryAxonSpanFactory")
    @ConditionalOnMissingBean(AxonSpanFactory.class)
    public AxonSpanFactory configureSpanFactory(List<SpanAttributesProvider> attributesProviders) {
        return new OpenTelemetryAxonSpanFactory(attributesProviders, false);
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
