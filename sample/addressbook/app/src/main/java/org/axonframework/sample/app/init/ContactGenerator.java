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

import org.axonframework.core.command.CommandBus;
import org.axonframework.sample.app.AddressType;
import org.axonframework.sample.app.command.CreateContactCommand;
import org.axonframework.sample.app.command.RegisterAddressCommand;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Allard Buijze
 */
public class ContactGenerator implements ApplicationListener {
    private CommandBus commandBus;
    private AtomicBoolean initialized = new AtomicBoolean();

    public ContactGenerator(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    public void onApplicationEvent(ApplicationEvent event) {
        if (!initialized.get() && event instanceof ContextRefreshedEvent) {
            initializeData();
        }
    }

    public void initializeData() {
        if (initialized.compareAndSet(false, true)) {
            CreateContactCommand commandAllard = new CreateContactCommand();
            commandAllard.setNewContactName("Allard");
            UUID uuidAllard = (UUID) commandBus.dispatch(commandAllard);

            CreateContactCommand commandJettro = new CreateContactCommand();
            commandJettro.setNewContactName("Jettro");
            UUID uuidJettro = (UUID) commandBus.dispatch(commandJettro);

            RegisterAddressCommand registerPrivateAddressCommand = new RegisterAddressCommand();
            registerPrivateAddressCommand.setAddressType(AddressType.PRIVATE);
            registerPrivateAddressCommand.setCity("The Hague");
            registerPrivateAddressCommand.setContactId(uuidAllard.toString());
            registerPrivateAddressCommand.setStreetAndNumber("AxonBoulevard 1");
            registerPrivateAddressCommand.setZipCode("1234AB");
            commandBus.dispatch(registerPrivateAddressCommand);

            RegisterAddressCommand registerWorkAddressCommand = new RegisterAddressCommand();
            registerWorkAddressCommand.setAddressType(AddressType.WORK);
            registerWorkAddressCommand.setCity("Amsterdam");
            registerWorkAddressCommand.setContactId(uuidAllard.toString());
            registerWorkAddressCommand.setStreetAndNumber("JTeam avenue");
            registerWorkAddressCommand.setZipCode("1234AB");
            commandBus.dispatch(registerWorkAddressCommand);

            RegisterAddressCommand registerJettroAddressCommand = new RegisterAddressCommand();
            registerJettroAddressCommand.setAddressType(AddressType.PRIVATE);
            registerJettroAddressCommand.setCity("Rotterdam");
            registerJettroAddressCommand.setContactId(uuidJettro.toString());
            registerJettroAddressCommand.setStreetAndNumber("Feyenoordlaan 010");
            registerJettroAddressCommand.setZipCode("3000AA");
            commandBus.dispatch(registerJettroAddressCommand);
        }
    }
}
