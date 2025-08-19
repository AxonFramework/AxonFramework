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

package org.axonframework.springboot.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.avro.message.SchemaStore;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.avro.AvroSerializer;
import org.axonframework.serialization.avro.AvroSerializerStrategy;
import org.axonframework.serialization.json.JacksonConverter;
import org.axonframework.springboot.ConverterProperties;
import org.axonframework.springboot.util.ConditionalOnMissingQualifiedBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * Autoconfiguration class dedicated to configuring the {@link org.axonframework.serialization.Converter}.
 * <p>
 * Users can influence the configuration through the {@link ConverterProperties}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@AutoConfiguration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@EnableConfigurationProperties(ConverterProperties.class)
public class ConverterAutoConfiguration {

    private final ApplicationContext applicationContext;
    private final ConverterProperties converterProperties;

    /**
     * Constructs an {@code ConverterAutoConfiguration} for the given {@code applicationContext} and
     * {@code converterProperties}.
     *
     * @param applicationContext  The application context used to validate for the existence of other required
     *                            properties to construct the specified
     *                            {@link org.axonframework.serialization.Converter Converters}.
     * @param converterProperties The {@link org.axonframework.serialization.Converter} properties defining which
     *                            {@code Converters} to register.
     */
    @Internal
    public ConverterAutoConfiguration(@Nonnull ApplicationContext applicationContext,
                                      @Nonnull ConverterProperties converterProperties) {
        this.applicationContext = requireNonNull(applicationContext, "The ApplicationContext cannot be null.");
        this.converterProperties = requireNonNull(converterProperties, "The ConverterProperties  cannot be null.");
    }

    @Bean
    @Primary
    @ConditionalOnMissingQualifiedBean(beanClass = Converter.class, qualifier = "!eventConverter,messageConverter")
    public Converter generalConverter() {
        ConverterProperties.ConverterType generalConverterType = converterProperties.getGeneral();
        if (ConverterProperties.ConverterType.AVRO == generalConverterType) {
            throw new AxonConfigurationException(format(
                    "Invalid converter type [%s] configured as general converter. "
                            + "The Avro Converter can be used as message or event serializer only.",
                    generalConverterType.name()
            ));
        }
        return buildSerializer(generalConverterType, null);
    }

    @Bean
    @Qualifier("messageConverter")
    @ConditionalOnMissingQualifiedBean(beanClass = Converter.class, qualifier = "messageConverter")
    public Converter messageConverter(Converter generalConverter) {
        ConverterProperties.ConverterType messagesConverterType = converterProperties.getMessages();
        if (ConverterProperties.ConverterType.DEFAULT == messagesConverterType
                || converterProperties.getGeneral() == messagesConverterType) {
            return generalConverter;
        }
        return buildSerializer(messagesConverterType, generalConverter);
    }

    @Bean
    @Qualifier("eventConverter")
    @ConditionalOnMissingQualifiedBean(beanClass = Converter.class, qualifier = "eventConverter")
    public Converter eventConverter(Converter generalConverter,
                                    @Qualifier("messageConverter") Converter messageConverter) {
        ConverterProperties.ConverterType eventsConverterType = converterProperties.getEvents();
        if (ConverterProperties.ConverterType.DEFAULT == eventsConverterType
                || converterProperties.getMessages() == eventsConverterType) {
            return messageConverter;
        } else if (converterProperties.getGeneral() == eventsConverterType) {
            return generalConverter;
        }
        return buildSerializer(eventsConverterType, generalConverter);
    }

    @Bean
    public ConfigurationEnhancer converterConfigurerEnhancer(Converter generalConverter,
                                                             @Qualifier("messageConverter") Converter messageConverter,
                                                             @Qualifier("eventConverter") Converter eventConverter) {
        // TODO I don't think we need this anymore...validate through tests
        return registry -> {
            registry.registerComponent(Converter.class, c -> generalConverter);
            registry.registerComponent(Converter.class, "messageConverter", c -> messageConverter);
            registry.registerComponent(Converter.class, "eventConverter", c -> eventConverter);
        };
    }

    private Converter buildSerializer(@Nonnull ConverterProperties.ConverterType converterType,
                                      @Nullable Converter generalConverter) {
        switch (converterType) {
            case AVRO:
                if (generalConverter == null) {
                    throw new AxonConfigurationException(
                            "General serializer is mandatory as a fallback Avro Converter, but none was provided."
                    );
                }
                Map<String, SchemaStore> schemaStoreBeans =
                        beansOfTypeIncludingAncestors(applicationContext, SchemaStore.class);
                SchemaStore schemaStore = schemaStoreBeans.containsKey("defaultAxonSchemaStore")
                        ? schemaStoreBeans.get("defaultAxonSchemaStore")
                        : schemaStoreBeans.values()
                                          .stream()
                                          .findFirst()
                                          .orElseThrow(() -> new NoSuchBeanDefinitionException(SchemaStore.class));

                Map<String, AvroSerializerStrategy> serializationStrategies = beansOfTypeIncludingAncestors(
                        applicationContext,
                        AvroSerializerStrategy.class);
                AvroSerializer.Builder builder = AvroSerializer.builder()
                                                               .schemaStore(schemaStore);
                                                               //.serializerDelegate(generalConverter);
                serializationStrategies.values().forEach(builder::addSerializerStrategy);
                // TODO #3609 - Rewrite to use the AvroConverter once that's in place.
                //return builder.build();
                return null;
            case CBOR:
                Map<String, CBORMapper> cborMapperBeans =
                        beansOfTypeIncludingAncestors(applicationContext, CBORMapper.class);
                ObjectMapper cborMapper = cborMapperBeans.containsKey("defaultAxonCborObjectMapper")
                        ? cborMapperBeans.get("defaultAxonCborObjectMapper")
                        : cborMapperBeans.values()
                                         .stream()
                                         .findFirst()
                                         .orElseThrow(() -> new NoSuchBeanDefinitionException(CBORMapper.class));
                // FIXME add Converter constructor option...
//                ChainingContentTypeConverter converter = new ChainingContentTypeConverter(beanClassLoader);
                return new JacksonConverter(cborMapper);
            case JACKSON:
            case DEFAULT:
            default:
                Map<String, ObjectMapper> objectMapperBeans =
                        beansOfTypeIncludingAncestors(applicationContext, ObjectMapper.class);
                ObjectMapper objectMapper = objectMapperBeans.containsKey("defaultAxonObjectMapper")
                        ? objectMapperBeans.get("defaultAxonObjectMapper")
                        : objectMapperBeans.values()
                                           .stream()
                                           .findFirst()
                                           .orElseThrow(() -> new NoSuchBeanDefinitionException(ObjectMapper.class));
                // FIXME add Converter constructor option...
//                ChainingContentTypeConverter converter = new ChainingContentTypeConverter(beanClassLoader);
                return new JacksonConverter(objectMapper);
        }
    }
}
