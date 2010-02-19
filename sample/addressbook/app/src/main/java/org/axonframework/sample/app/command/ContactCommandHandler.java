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

import org.axonframework.sample.app.Address;
import org.axonframework.sample.app.AddressType;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * <p>Command handler that can be used to create and update Contacts. It can also be used to register and remove
 * addresses.</p>
 * <p>The provided repository is used to store the changes.</p>
 *
 * @author Allard Buijze
 */
public class ContactCommandHandler {

    private ContactRepository repository;

    /**
     * Creates a contact with the provided name.
     *
     * @param name String required field that contains the contact of the name to be created.
     * @return UUID of the person that is created.
     */
    public UUID createContact(String name) {
        Assert.notNull(name, "Name may not be null");
        Contact contact = new Contact(name);
        repository.save(contact);
        return contact.getIdentifier();
    }

    /**
     * Changes the name of the contact with the provided UUID. An {@code AggregateNotFoundException}
     * is thrown if the UUID does not represent a valid contact.
     *
     * @param contactId UUID required field representing the contact to change
     * @param name      String required field containing the new value of the name of the contact
     */
    public void changeContactName(UUID contactId, String name) {
        Assert.notNull(contactId, "ContactIdentifier may not be null");
        Assert.notNull(name, "Name may not be null");
        Contact contact = repository.load(contactId);
        contact.changeName(name);
        repository.save(contact);
    }

    /**
     * Registers an address for the contact with the provided UUID. If the contact already has an address with the
     * provided type, this address will be updated. An {@code AggregateNotFoundException} is thrown if the provided
     * UUID does not exist.
     *
     * @param contactId UUID required field containing the identifier of the contact to add an address to.
     * @param type      AddressType required field containing the type of the address to register.
     * @param address   Address Value object that contains the data of the new address to register
     */
    public void registerAddress(UUID contactId, AddressType type, Address address) {
        Assert.notNull(contactId, "ContactIdentifier may not be null");
        Assert.notNull(type, "AddressType may not be null");
        Assert.notNull(address, "Address may not be null");
        Contact contact = repository.load(contactId);
        contact.registerAddress(type, address);
        repository.save(contact);
    }

    /**
     * Removes the address with the specified type from the contact with the provided UUID. If the UUID does not exist,
     * an {@code AggregateNotFoundException} is thrown. If the contact does not have an address with specified type
     * nothing happens.
     *
     * @param contactId UUID required field containing the identifier of the contact to remove the address from
     * @param type      AddressType required field containing the type of the address to remove from the contact
     */
    public void removeAddress(UUID contactId, AddressType type) {
        Assert.notNull(contactId, "ContactIdentifier may not be null");
        Assert.notNull(type, "AddressType may not be null");
        Contact contact = repository.load(contactId);
        contact.removeAddress(type);
        repository.save(contact);
    }

    /**
     * Delete a contact with the provided UUID
     *
     * @param contactId UUID required field containing the identifier of the contact to be removed
     */
    public void deleteContact(UUID contactId) {
        Assert.notNull(contactId, "ContactIdentifier may not be null");
        repository.delete(contactId);
    }

    public void setRepository(ContactRepository repository) {
        this.repository = repository;
    }
}
