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

package org.axonframework.sample.app;

import org.axonframework.eventstore.EventStore;
import org.axonframework.repository.Repository;
import org.axonframework.sample.app.command.ContactCommandHandler;
import org.axonframework.sample.app.query.ContactRepository;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:/META-INF/spring/application-context.xml",
        "classpath:/META-INF/spring/database-context.xml"})
public class ContactIntegrationTest {

    @Autowired
    private ContactCommandHandler commandHandler;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private Repository commandRepository;

    @Test(timeout = 10000)
    public void testApplicationContext() throws InterruptedException {
        assertNotNull(commandHandler);
        assertNotNull(eventStore);
        assertNotNull(taskExecutor);
        assertNotNull(contactRepository);
        assertNotNull(commandRepository);
    }

}
