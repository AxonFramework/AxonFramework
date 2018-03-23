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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaPublisherConfiguration}
 *
 * @author Nakul Mishra
 */
public class KafkaPublisherConfigurationTests {

    @Test
    @SuppressWarnings("unchecked")
    public void testGeneratingValidPublisherConfig() {
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

        assertThat(conf.getMessageSource(), is(source));
        assertThat(conf.getProducerFactory(), is(factory));
        assertThat(conf.getMessageConverter(), is(converter));
        assertThat(conf.getMessageMonitor(), is(monitor));
        assertThat(conf.getTopic(), is(topic));
        assertThat(conf.getPublisherAckTimeout(), is(timeout));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalidMessageSource() {
        KafkaPublisherConfiguration.builder().withMessageSource(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalidProducerFactory() {
        KafkaPublisherConfiguration.builder().withProducerFactory(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalidMessageConverter() {
        KafkaPublisherConfiguration.builder().withMessageConverter(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalidMessageMonitor() {
        KafkaPublisherConfiguration.builder().withMessageMonitor(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalidKafkaTopic() {
        KafkaPublisherConfiguration.builder().withTopic(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfiguringInvalidAckTimeout() {
        KafkaPublisherConfiguration.builder().withPublisherAckTimeout((long) -12);
    }
}