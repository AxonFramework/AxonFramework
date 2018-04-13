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

import org.axonframework.kafka.eventhandling.KafkaMessageConverter;
import org.axonframework.messaging.SubscribableMessageSource;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaPublisherConfiguration}.
 *
 * @author Nakul Mishra
 */
public class KafkaPublisherConfigurationTests {

    @Test
    @SuppressWarnings("unchecked")
    public void testGenerating_ValidPublisherConfig() {
        SubscribableMessageSource source = mock(SubscribableMessageSource.class);
        ProducerFactory factory = mock(ProducerFactory.class);
        KafkaMessageConverter converter = mock(KafkaMessageConverter.class);
        MessageMonitor monitor = mock(MessageMonitor.class);
        String topic = "foo";
        long timeout = 1;
        KafkaPublisherConfiguration conf = KafkaPublisherConfiguration
                .builder()
                .withMessageSource(source)
                .withProducerFactory(factory)
                .withMessageConverter(converter)
                .withMessageMonitor(monitor)
                .withTopic(topic)
                .withPublisherAckTimeout(timeout)
                .build();
        assertThat(conf.getMessageSource()).isEqualTo(source);
        assertThat(conf.getProducerFactory()).isEqualTo(factory);
        assertThat(conf.getMessageConverter()).isEqualTo(converter);
        assertThat(conf.getMessageMonitor()).isEqualTo(monitor);
        assertThat(conf.getTopic()).isEqualTo(topic);
        assertThat(conf.getPublisherAckTimeout()).isEqualTo(timeout);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalid_MessageSource() {
        KafkaPublisherConfiguration.builder().withMessageSource(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalid_ProducerFactory() {
        KafkaPublisherConfiguration.builder().withProducerFactory(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalid_MessageConverter() {
        KafkaPublisherConfiguration.builder().withMessageConverter(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalid_MessageMonitor() {
        KafkaPublisherConfiguration.builder().withMessageMonitor(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalid_KafkaTopic() {
        KafkaPublisherConfiguration.builder().withTopic(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalid_AckTimeout() {
        KafkaPublisherConfiguration.builder().withPublisherAckTimeout((long) -12);
    }
}