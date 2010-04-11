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

package org.axonframework.sample.app.command;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.repository.Repository;
import org.axonframework.sample.app.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * <p>Command handler that can be used to create and update Contacts. It can also be used to register and remove
 * addresses.</p> <p>The provided repository is used to store the changes.</p>
 *
 * @author Allard Buijze
 */
public class ContactCommandHandler {

    private final static Logger logger = LoggerFactory.getLogger(ContactCommandHandler.class);
    private Repository<Contact> repository;

    public void setRepository(Repository<Contact> repository) {
        this.repository = repository;
    }

    /**
     * Creates a new contact based on the provided data
     *
     * @param command CreateContactCommand object that contains the needed data to create a new contact
     */
    @CommandHandler
    public UUID handle(CreateContactCommand command) {
        logger.debug("Received a command for a new contact with name : {}", command.getNewContactName());
        Assert.notNull(command.getNewContactName(), "Name may not be null");
        Contact contact = new Contact(command.getNewContactName());
        repository.save(contact);
        return contact.getIdentifier();
    }

    /**
     * Changes the provided data for the contact found based on the provided identifier
     * <p/>
     * An {@code AggregateNotFoundException} is thrown if the UUID does not represent a valid contact.
     *
     * @param command ChangeContactNameCommand that contains the identifier and the data to be updated
     */
    @CommandHandler
    public void handle(ChangeContactNameCommand command) {
        Assert.notNull(command.getContactId(), "ContactIdentifier may not be null");
        Assert.notNull(command.getContactNewName(), "Name may not be null");
        Contact contact = repository.load(UUID.fromString(command.getContactNewName()));
        contact.changeName(command.getContactNewName());
        repository.save(contact);
    }

    /**
     * Removes the contact belonging to the contactId as provided by the command
     *
     * @param command RemoveContactCommand containing the identifier of the contact to be removed
     */
    @CommandHandler
    public void handle(RemoveContactCommand command) {
        Assert.notNull(command.getContactId(), "ContactIdentifier may not be null");
        Contact contact = repository.load(UUID.fromString(command.getContactId()));
        contact.delete();
        repository.save(contact);
    }

    /**
     * Registers an address for the contact with the provided UUID. If the contact already has an address with the
     * provided type, this address will be updated. An {@code AggregateNotFoundException} is thrown if the provided UUID
     * does not exist.
     *
     * @param command RegisterAddressCommand that contains all required data
     */
    @CommandHandler
    public void handle(RegisterAddressCommand command) {
        Assert.notNull(command.getContactId(), "ContactIdentifier may not be null");
        Assert.notNull(command.getAddressType(), "AddressType may not be null");
        Address address = new Address(command.getStreetAndNumber(), command.getZipCode(), command.getCity());
        Contact contact = repository.load(UUID.fromString(command.getContactId()));
        contact.registerAddress(command.getAddressType(), address);
        repository.save(contact);

    }

    /**
     * Removes the address with the specified type from the contact with the provided UUID. If the UUID does not exist,
     * an {@code AggregateNotFoundException} is thrown. If the contact does not have an address with specified type
     * nothing happens.
     *
     * @param command RemoveAddressCommand that contains all required data to remove an address from a contact
     */
    @CommandHandler
    public void handle(RemoveAddressCommand command) {
        Assert.notNull(command.getContactId(), "ContactIdentifier may not be null");
        Assert.notNull(command.getAddressType(), "AddressType may not be null");
        Contact contact = repository.load(UUID.fromString(command.getContactId()));
        contact.removeAddress(command.getAddressType());
        repository.save(contact);
    }
}
