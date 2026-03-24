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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.conversion.ChainingContentTypeConverter;
import org.axonframework.conversion.Converter;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.extension.spring.data.JacksonPageDeserializer;
import org.axonframework.extension.springboot.ConverterProperties;
import org.axonframework.messaging.core.conversion.DelegatingMessageConverter;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import static java.util.Objects.requireNonNull;

/**
 * Autoconfiguration that constructs a default {@link ObjectMapper}, typically to be used by a
 * {@link org.axonframework.conversion.jackson.JacksonConverter}.
 *
 * @author Steven van Beelen
 * @author Theo Emanuelsson
 * @since 3.4.0
 */
@AutoConfiguration
@AutoConfigureBefore({AxonAutoConfiguration.class, CBORMapperAutoConfiguration.class})
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
})
@ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")
@EnableConfigurationProperties(value = ConverterProperties.class)
public class ObjectMapperAutoConfiguration implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    /**
     * Bean creation method constructing a {@link JacksonConverter} as the "general" {@link Converter} to be used by
     * Axon Framework.
     * <p>
     * This bean acts as fallback and gets created in case {@code axon.converter.general} is not set or set to
     * {@code default}.
     *
     * @param objectMapper the {@link ObjectMapper} to be used
     * @return the "general" {@link Converter} to be used by Axon Framework
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(ignored = {MessageConverter.class, EventConverter.class})
    @ConditionalOnExpression("'${axon.converter.general}' == 'jackson' || '${axon.converter.general:default}' == 'default'")
    public Converter converter(ObjectMapper objectMapper) {
        return buildConverter(objectMapper);
    }

    /**
     * Bean creation method constructing a {@link MessageConverter} delegating to the "general" {@link Converter} in
     * case both use {@code jackson/default}.
     *
     * @param generalConverter the "general" {@link Converter}, used to construct the {@link MessageConverter} in case
     *                         both use {@code jackson/default}
     * @return the {@link MessageConverter} to be used by Axon Framework
     */
    @Bean(name = "messageConverter")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("""
            '${axon.converter.messages}' == 'jackson'
            && ('${axon.converter.general}' == 'jackson' || '${axon.converter.general:default}' == 'default')
            """)
    public MessageConverter delegatingMessageConverter(Converter generalConverter) {
        return new DelegatingMessageConverter(generalConverter);
    }

    /**
     * Bean creation method constructing a {@link JacksonConverter} as the {@link MessageConverter} to be used by Axon
     * Framework.
     *
     * @param objectMapper the {@link ObjectMapper} to be used
     * @return The {@link MessageConverter} to be used by Axon Framework.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("""
            '${axon.converter.messages}' == 'jackson'
            && !('${axon.converter.general}' == 'jackson' || '${axon.converter.general:default}' == 'default')
            """)
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new DelegatingMessageConverter(buildConverter(objectMapper));
    }

    /**
     * Bean creation method constructing an {@link EventConverter} delegating to the {@link MessageConverter} in case
     * both use {@code jackson}.
     *
     * @param messageConverter the {@link MessageConverter}, used to construct the {@link EventConverter} in case both
     *                         use {@code jackson}
     * @return the {@link EventConverter} to be used by Axon Framework
     */
    @Bean(name = "eventConverter")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${axon.converter.events}' == 'jackson' && '${axon.converter.messages}' == 'jackson'")
    public EventConverter delegatingEventConverter(MessageConverter messageConverter) {
        return new DelegatingEventConverter(messageConverter);
    }

    /**
     * Bean creation method constructing a {@link JacksonConverter} as the {@link EventConverter} to be used by Axon
     * Framework.
     *
     * @param objectMapper the {@link ObjectMapper} to be used
     * @return The {@link EventConverter} to be used by Axon Framework.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${axon.converter.events}' == 'jackson' && '${axon.converter.messages}' != 'jackson'")
    public EventConverter eventConverter(ObjectMapper objectMapper) {
        return new DelegatingEventConverter(buildConverter(objectMapper));
    }

    private Converter buildConverter(ObjectMapper objectMapper) {
        return new JacksonConverter(objectMapper, new ChainingContentTypeConverter(classLoader));
    }


    /**
     * Returns the default Axon Framework {@link ObjectMapper}, if required.
     * <p>
     * This {@code ObjectMapper} bean is only created when there is no other {@code ObjectMapper} bean present
     * <b>and</b> whenever the user specified either the
     * {@link ConverterProperties.ConverterType#DEFAULT} or {@link ConverterProperties.ConverterType#JACKSON}
     * {@code ConverterType}.
     *
     * @param modules An {@link ObjectProvider} of {@link JacksonModule} beans to register with the
     *                {@link ObjectMapper}.
     * @return The default Axon Framework {@link ObjectMapper}, if required.
     */
    @Bean("defaultAxonObjectMapper")
    @Primary
    @ConditionalOnMissingBean
    @Conditional(JacksonConfiguredCondition.class)
    public ObjectMapper defaultAxonObjectMapper(ObjectProvider<JacksonModule> modules) {
        JsonMapper.Builder builder = JsonMapper.builder().findAndAddModules();
        modules.orderedStream().forEach(builder::addModule);
        return builder.build();
    }

    /**
     * Returns a Jackson {@link Module} that provides a custom deserializer for the Spring Data {@link Page} interface.
     * <p>
     * This {@code Module} bean is only created when the Spring Data {@link Page} interface is on the classpath
     * <b>and</b> whenever the user specified either the {@link ConverterProperties.ConverterType#DEFAULT} or
     * {@link ConverterProperties.ConverterType#JACKSON} {@code ConverterType}. The module deserializes {@link Page}
     * instances into {@link org.springframework.data.domain.PageImpl PageImpl} objects.
     *
     * @return A Jackson {@link Module} that deserializes {@link Page} into
     * {@link org.springframework.data.domain.PageImpl PageImpl}.
     * @see JacksonPageDeserializer
     * @since 5.1.0
     */
    @Bean
    @ConditionalOnClass(Page.class)
    @Conditional(JacksonConfiguredCondition.class)
    public JacksonModule springDataPageJacksonModule() {
        return new SimpleModule()
                .addDeserializer(Page.class, new JacksonPageDeserializer());
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

    /**
     * Sets the class loader used by the {@link ChainingContentTypeConverter} to load
     * {@link org.axonframework.conversion.ContentTypeConverter ContentTypeConverters}.
     *
     * @param classLoader The class loader used by the {@link ChainingContentTypeConverter} to load
     *                    {@link org.axonframework.conversion.ContentTypeConverter ContentTypeConverters}.
     */
    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader, "The ClassLoader cannot be null.");
    }
}
