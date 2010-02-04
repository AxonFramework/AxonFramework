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

package org.axonframework.sample.app.init;

import org.axonframework.sample.app.Address;
import org.axonframework.sample.app.AddressType;
import org.axonframework.sample.app.command.ContactCommandHandler;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Allard Buijze
 */
public class ContactGenerator implements ApplicationListener {

    private ContactCommandHandler contactCommandHandler;
    private AtomicBoolean initialized = new AtomicBoolean();

    public ContactGenerator(ContactCommandHandler contactCommandHandler) {
        this.contactCommandHandler = contactCommandHandler;
    }

    public void onApplicationEvent(ApplicationEvent event) {
        if (!initialized.get() && event instanceof ContextRefreshedEvent) {
            initializeData();
        }
    }

    public void initializeData() {
        if (initialized.compareAndSet(false, true)) {
            UUID contact1 = contactCommandHandler.createContact("Allard");
            contactCommandHandler.registerAddress(contact1, AddressType.PRIVATE, new Address("AxonBoulevard 1",
                                                                                             "1234AB",
                                                                                             "The Hague"));
            contactCommandHandler.registerAddress(contact1, AddressType.WORK, new Address("JTeam avenue",
                                                                                          "1234AB",
                                                                                          "Amsterdam"));

            UUID contact2 = contactCommandHandler.createContact("Jettro");
            contactCommandHandler.registerAddress(contact2, AddressType.PRIVATE, new Address("Feyenoordlaan 010",
                                                                                             "3000AA",
                                                                                             "Rotterdam"));
        }
    }
}
