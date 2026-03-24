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

import org.axonframework.extension.springboot.ConverterProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * Autoconfiguration that constructs a default {@link CBORMapper}, typically to be used by a
 * {@link org.axonframework.conversion.jackson.JacksonConverter}.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
@AutoConfiguration
@AutoConfigureBefore({ConverterAutoConfiguration.class, CBORConverterAutoConfiguration.class})
@ConditionalOnClass(name = {"tools.jackson.dataformat.cbor.CBORMapper"})
public class CBORMapperAutoConfiguration {

    /**
     * Returns the default Axon Framework {@link CBORMapper}, if required.
     * <p>
     * This {@code CBORMapper} bean is only created when there is no other {@code CBORMapper} bean present
     * <b>and</b> whenever the user specified the
     * {@link ConverterProperties.ConverterType#CBOR} {@code ConverterType}.
     *
     * @return The default Axon Framework {@link CBORMapper}, if required.
     */
    @Bean("defaultAxonCborMapper")
    @ConditionalOnMissingBean(CBORMapper.class)
    @Conditional(CborConfiguredCondition.class)
    public CBORMapper defaultAxonCborMapper() {
        return CBORMapper.builder().findAndAddModules().build();
    }

    /**
     * An {@link AnyNestedCondition} implementation, to support the following use cases:
     * <ul>
     *     <li>The {@code general} converter property is set to {@code cbor}</li>
     *     <li>The {@code messages} converter property is set to {@code cbor}</li>
     *     <li>The {@code events} converter property is set to {@code cbor}</li>
     * </ul>
     */
    private static class CborConfiguredCondition extends AnyNestedCondition {

        public CborConfiguredCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.general", havingValue = "cbor")
        static class GeneralJacksonCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.messages", havingValue = "cbor")
        static class MessagesJacksonCondition {

        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "axon.converter.events", havingValue = "cbor")
        static class EventsJacksonCondition {

        }
    }
}
