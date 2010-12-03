/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.saga.timer;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.repository.SagaRepository;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/META-INF/spring/saga-integration-text.xml")
public class QuartzSagaTimerIntegrationTest {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private SagaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    public void testApplicationContext_Startup() throws InterruptedException {
        assertNotNull(eventBus);
        final String randomAssociationValue = UUID.randomUUID().toString();
        EventListener listener = mock(EventListener.class);
        eventBus.subscribe(listener);

        SimpleTimingSaga saga = new TransactionTemplate(transactionManager)
                .execute(new TransactionCallback<SimpleTimingSaga>() {
                    @Override
                    public SimpleTimingSaga doInTransaction(TransactionStatus status) {
                        eventBus.publish(new StartingEvent(this, randomAssociationValue));
                        Set<SimpleTimingSaga> actualResult =
                                repository.find(SimpleTimingSaga.class, new AssociationValue("association",
                                                                                             randomAssociationValue));
                        assertEquals(1, actualResult.size());
                        return actualResult.iterator().next();
                    }
                });

        saga.waitForEventProcessing(10000);
        assertTrue("Expected saga to be triggered", saga.isTriggered());
    }

}
