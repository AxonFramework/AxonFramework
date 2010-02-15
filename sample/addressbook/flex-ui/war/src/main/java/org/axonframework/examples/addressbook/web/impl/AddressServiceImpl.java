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

package org.axonframework.examples.addressbook.web.impl;

import org.axonframework.examples.addressbook.web.AddressService;
import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.axonframework.examples.addressbook.web.dto.ContactDTO;
import org.axonframework.sample.app.Address;
import org.axonframework.sample.app.AddressType;
import org.axonframework.sample.app.command.ContactCommandHandler;
import org.axonframework.sample.app.query.AddressEntry;
import org.axonframework.sample.app.query.ContactEntry;
import org.axonframework.sample.app.query.ContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.flex.remoting.RemotingDestination;
import org.springframework.flex.remoting.RemotingInclude;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Jettro Coenradie
 */
@Service("addressService")
@RemotingDestination(channels = {"my-amf"})
public class AddressServiceImpl implements AddressService {
    private ContactCommandHandler contactCommandHandler;
    private ContactRepository repository;

    @Autowired
    public AddressServiceImpl(ContactCommandHandler contactCommandHandler, ContactRepository repository) {
        this.contactCommandHandler = contactCommandHandler;
        this.repository = repository;
    }

    @RemotingInclude
    @Override
    public List<AddressDTO> searchAddresses(AddressDTO searchAddress) {
        List<AddressDTO> foundAddresses = new ArrayList<AddressDTO>();

        List<AddressEntry> addresses =
                repository.findAllAddressesInCity(searchAddress.getContactName(), searchAddress.getCity());
        for (AddressEntry address : addresses) {
            foundAddresses.add(AddressDTO.createFrom(address));
        }
        return foundAddresses;
    }

    @RemotingInclude
    @Override
    public void createAddress(AddressDTO addressDTO) {
        Address address = new Address(addressDTO.getStreet(), addressDTO.getZipCode(), addressDTO.getCity());
        contactCommandHandler.registerAddress(UUID.fromString(addressDTO.getContactUUID()), addressDTO.getType(), address);
    }

    @Override
    @RemotingInclude
    public List<ContactDTO> obtainAllContacts() {
        List<ContactDTO> contacts = new ArrayList<ContactDTO>();
        List<ContactEntry> allContacts = repository.findAllContacts();
        for (ContactEntry contactEntry : allContacts) {
            contacts.add(ContactDTO.createContactDTOFrom(contactEntry));
        }
        return contacts;
    }

    @Override
    @RemotingInclude
    public void createContact(ContactDTO contactDTO) {
        contactCommandHandler.createContact(contactDTO.getName());
    }

    @Override
    @RemotingInclude
    public List<AddressDTO> obtainContactAddresses(String contactIdentifier) {
        List<AddressDTO> foundAddresses = new ArrayList<AddressDTO>();

        List<AddressEntry> addressesForContact =
                repository.findAllAddressesForContact(UUID.fromString(contactIdentifier));
        for (AddressEntry entry : addressesForContact) {
            foundAddresses.add(AddressDTO.createFrom(entry));
        }
        return foundAddresses;
    }

    @Override
    @RemotingInclude
    public void removeAddressFor(String contactIdentifier, AddressType addressType) {
        contactCommandHandler.removeAddress(UUID.fromString(contactIdentifier), addressType);
    }

    @Override
    @RemotingInclude
    public void removeContact(String contactIdentifier) {
        contactCommandHandler.deleteContact(UUID.fromString(contactIdentifier));
    }
}
