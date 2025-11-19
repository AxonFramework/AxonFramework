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

package org.axonframework.extension.springboot.autoconfig;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.axonframework.extension.springboot.ConverterProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Autoconfiguration class dedicated to providing custom Jackson modules for the {@link ObjectMapper}.
 * <p>
 * This autoconfiguration is registered before the {@link ObjectMapperAutoConfiguration} and
 * {@link AxonAutoConfiguration} to ensure custom modules are available when the {@link ObjectMapper} is
 * constructed.
 *
 * @author Theo Emanuelsson
 * @since 5.0.0
 */
@AutoConfiguration
@AutoConfigureBefore({AxonAutoConfiguration.class, ObjectMapperAutoConfiguration.class})
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
@EnableConfigurationProperties(value = ConverterProperties.class)
public class CustomJacksonModuleAutoConfiguration {

    /**
     * Returns a Jackson {@link Module} that provides a custom deserializer for the Spring Data {@link Page} interface.
     * <p>
     * This {@code Module} bean is only created when the Spring Data {@link Page} interface is on the classpath
     * <b>and</b> whenever the user specified either the {@link ConverterProperties.ConverterType#DEFAULT} or
     * {@link ConverterProperties.ConverterType#JACKSON} {@code ConverterType}. The module deserializes {@link Page}
     * instances into {@link PageImpl} objects.
     *
     * @return A Jackson {@link Module} that deserializes {@link Page} into {@link PageImpl}.
     * @see JacksonPageDeserializer
     */
    @Bean
    @ConditionalOnClass(Page.class)
    @Conditional(JacksonConfiguredCondition.class)
    public Module springDataPageJacksonModule() {
        return new SimpleModule()
                .addDeserializer(Page.class, new JacksonPageDeserializer());
    }

    /**
     * Custom Jackson deserializer for the Spring Data {@link Page} interface.
     * <p>
     * This deserializer converts JSON representations of paginated data into {@link PageImpl} instances. It extracts
     * the {@code content} array, {@code number} (page number), {@code size} (page size), and {@code totalElements}
     * from the JSON structure.
     *
     * @see CustomJacksonModuleAutoConfiguration#springDataPageJacksonModule()
     */
    private static class JacksonPageDeserializer extends JsonDeserializer<Page<?>> {

        @Override
        public Page<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            List<Object> content = new ArrayList<>();
            JsonNode contentNode = node.get("content");
            if (contentNode != null && contentNode.isArray()) {
                contentNode.forEach(content::add);
            }

            int page = node.has("number") ? node.get("number").asInt() : 0;
            int size = node.has("size") ? node.get("size").asInt() : Math.max(content.size(), 1);
            long totalElements = node.has("totalElements") ? node.get("totalElements").asLong() : content.size();

            return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
        }
    }

    /**
     * An {@link AnyNestedCondition} implementation, to support the following use cases:
     * <ul>
     *     <li>The {@code general} converter property is not set. This means Axon defaults to Jackson</li>
     *     <li>The {@code general} converter property is set to {@code default}. This means Jackson will be used</li>
     *     <li>The {@code general} converter property is set to {@code jackson}</li>
     *     <li>The {@code messages} converter property is set to {@code jackson}</li>
     *     <li>The {@code events} converter property is set to {@code jackson}</li>
     * </ul>
     */
    private static class JacksonConfiguredCondition extends AnyNestedCondition {

        public JacksonConfiguredCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.general", havingValue = "default", matchIfMissing = true)
        static class GeneralDefaultCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.general", havingValue = "jackson", matchIfMissing = true)
        static class GeneralJacksonCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.messages", havingValue = "jackson")
        static class MessagesJacksonCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.events", havingValue = "jackson")
        static class EventsJacksonCondition {

        }
    }
}
