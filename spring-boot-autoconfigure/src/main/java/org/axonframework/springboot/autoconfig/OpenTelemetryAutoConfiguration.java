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

import org.axonframework.tracing.SpanAttributesProvider;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.opentelemetry.OpenTelemetrySpanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Automatically configured the {@link OpenTelemetrySpanFactory} as the method of providing tracing in Axon Framework.
 * For this to take effect, the {@code axon-tracing-opentelemetry} dependency must be on the classpath.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
@Configuration
@AutoConfigureBefore({AxonTracingAutoConfiguration.class, AxonAutoConfiguration.class})
@ConditionalOnClass(name = "org.axonframework.tracing.opentelemetry.OpenTelemetrySpanFactory")
public class OpenTelemetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpanFactory.class)
    public SpanFactory spanFactory(List<SpanAttributesProvider> attributesProviders) {
        return OpenTelemetrySpanFactory.builder()
                                       .addSpanAttributeProviders(attributesProviders)
                                       .build();
    }
}
