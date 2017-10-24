/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.config;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.StartSaga;
import org.junit.*;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SagaConfigurationTest {

    @Test
    public void testConfigureMonitor() throws Exception {
        MessageCollectingMonitor subscribingMonitor = new MessageCollectingMonitor();
        MessageCollectingMonitor trackingMonitor = new MessageCollectingMonitor(1);

        SagaConfiguration<Object> subscribing = SagaConfiguration.subscribingSagaManager(Object.class);
        subscribing.configureMessageMonitor(subscribingMonitor);
        SagaConfiguration<TestSaga> tracking = SagaConfiguration.trackingSagaManager(TestSaga.class);
        tracking.configureMessageMonitor(trackingMonitor);
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.registerModule(subscribing);
        configurer.registerModule(tracking);
        Configuration config = configurer.start();

        try {
            config.eventBus().publish(new GenericEventMessage<>("test"));

            assertEquals(1, subscribingMonitor.getMessages().size());
            assertTrue(trackingMonitor.await(10, TimeUnit.SECONDS));
        } finally {
            config.shutdown();
        }
    }

    public static class TestSagaEvent {
        private String id;
        TestSagaEvent(String id) {
            this.id = id;
        }
        public String getId() {
            return id;
        }
    }

    public static class TestSaga {
        @StartSaga
        @SagaEventHandler(associationProperty = "id")
        public void handleEvent(TestSagaEvent event) {
            System.out.println("yo " + event.getId());
        }
    }
}
