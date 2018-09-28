/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.kafka.eventhandling.producer;

import org.axonframework.common.AxonConfigurationException;
import org.junit.*;

/**
 * Tests for {@link KafkaPublisher.Builder}.
 *
 * @author Nakul Mishra
 */
public class KafkaPublisherBuilderTests {

    @Test(expected = AxonConfigurationException.class)
    public void testConfiguringInvalidMessageSource() {
        KafkaPublisher.builder().messageSource(null);
    }

    @Test(expected = AxonConfigurationException.class)
    public void testConfiguringInvalidProducerFactory() {
        KafkaPublisher.builder().producerFactory(null).build();
    }

    @Test(expected = AxonConfigurationException.class)
    public void testConfiguringInvalidMessageConverter() {
        KafkaPublisher.builder().messageConverter(null).build();
    }

    @Test(expected = AxonConfigurationException.class)
    public void testConfiguringInvalidMessageMonitor() {
        KafkaPublisher.builder().messageMonitor(null).build();
    }

    @Test(expected = AxonConfigurationException.class)
    public void testConfiguringInvalidKafkaTopic() {
        KafkaPublisher.builder().topic(null).build();
    }

    @Test(expected = AxonConfigurationException.class)
    public void testConfiguringInvalidAckTimeout() {
        KafkaPublisher.builder().publisherAckTimeout((long) -12);
    }
}