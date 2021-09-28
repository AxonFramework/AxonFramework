/*
 * Copyright (c) 2010-2021. Axon Framework
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

import com.thoughtworks.xstream.XStream;
import org.axonframework.serialization.xml.CompactDriver;
import org.axonframework.springboot.SerializerProperties;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Autoconfigures an {@link XStream} instance in absence of an existing {@code XStream} bean.
 * <p>
 * Will automatically set the security context of the {@code XStream} instance, based on the classes and packages found
 * in the {@link ComponentScan} annotated classes.
 *
 * @author Steven van Beelen
 * @since 4.5.4
 */
@Configuration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(name = "com.thoughtworks.xstream.XStream")
@EnableConfigurationProperties(value = SerializerProperties.class)
public class XStreamAutoConfiguration {

    @Bean("defaultAxonXStream")
    @ConditionalOnMissingBean
    @Conditional(XStreamConfiguredCondition.class)
    public XStream defaultAxonXStream(ApplicationContext applicationContext) {
        XStream xStream = new XStream(new CompactDriver());
        xStream.allowTypesByWildcard(getTypesFromComponentScanAnnotatedBeans(applicationContext));
        return xStream;
    }

    // TODO: 28-09-21 move to utility class for testing
    private static String[] getTypesFromComponentScanAnnotatedBeans(ApplicationContext applicationContext) {
        return applicationContext.getBeansWithAnnotation(ComponentScan.class)
                                 .entrySet().stream()
                                 .flatMap(entry -> {
                                     ComponentScan ann = applicationContext.findAnnotationOnBean(
                                             entry.getKey(), ComponentScan.class
                                     );
                                     if (!Objects.nonNull(ann)) {
                                         throw new IllegalArgumentException(
                                                 "The ApplicationContext retrieved a bean of name [" + entry.getKey()
                                                         + "] and type [" + entry.getValue().getClass()
                                                         + "] for the ComponentScan annotation which does not contain the ComponentScan annotation."
                                         );
                                     }

                                     if (ann.basePackageClasses().length != 0
                                             || ann.basePackages().length != 0) {
                                         Stream<String> basePackageClasses =
                                                 Arrays.stream(ann.basePackageClasses())
                                                       .map(Class::getName);

                                         Stream<String> basePackages =
                                                 Arrays.stream(ann.basePackages())
                                                       .map(basePackage -> basePackage + ".**");

                                         return Stream.concat(basePackageClasses, basePackages);
                                     } else {
                                         return Stream.of(entry.getValue().getClass().getPackage().getName() + ".**");
                                     }
                                 })
                                 .toArray(String[]::new);
    }

    /**
     * An {@link AnyNestedCondition} implementation, to support the following use cases:
     * <ul>
     *     <li>The {@code general} serializer property is not set. This means Axon defaults to XStream</li>
     *     <li>The {@code general} serializer property is set to {@code default}. This means XStream will be used</li>
     *     <li>The {@code general} serializer property is set to {@code xstream}</li>
     *     <li>The {@code messages} serializer property is set to {@code xstream}</li>
     *     <li>The {@code events} serializer property is set to {@code xstream}</li>
     * </ul>
     */
    private static class XStreamConfiguredCondition extends AnyNestedCondition {

        public XStreamConfiguredCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.serializer.general", havingValue = "default", matchIfMissing = true)
        static class GeneralDefaultCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.serializer.general", havingValue = "xstream", matchIfMissing = true)
        static class GeneralXStreamCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.serializer.messages", havingValue = "xstream")
        static class MessagesXStreamCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.serializer.events", havingValue = "xstream")
        static class EventsXStreamCondition {

        }
    }
}
