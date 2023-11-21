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

import com.thoughtworks.xstream.XStream;
import org.axonframework.serialization.xml.CompactDriver;
import org.axonframework.springboot.SerializerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.lang.invoke.MethodHandles;

import static org.axonframework.springboot.util.XStreamSecurityTypeUtility.autoConfigBasePackages;

/**
 * Autoconfigures an {@link XStream} instance in absence of an existing {@code XStream} bean.
 * <p>
 * Will automatically set the security context of the {@code XStream} instance, based on the auto-configuration base
 * packages.
 *
 * @author Steven van Beelen
 * @since 4.5.4
 */
@AutoConfiguration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@ConditionalOnClass(name = "com.thoughtworks.xstream.XStream")
@EnableConfigurationProperties(value = SerializerProperties.class)
public class XStreamAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Bean("defaultAxonXStream")
    @ConditionalOnMissingBean
    @Conditional(XStreamConfiguredCondition.class)
    public XStream defaultAxonXStream(ApplicationContext applicationContext) {
        logger.info("Initializing an XStream instance since none was found. The auto configuration base packages will be used as wildcards for the XStream security settings.");
        XStream xStream = new XStream(new CompactDriver());
        xStream.allowTypesByWildcard(autoConfigBasePackages(applicationContext));
        return xStream;
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
