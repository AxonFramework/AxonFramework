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
import org.axonframework.sample.app.Address;
import org.axonframework.sample.app.AddressType;
import org.axonframework.sample.app.command.ContactCommandHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.flex.remoting.RemotingDestination;
import org.springframework.flex.remoting.RemotingInclude;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @author Jettro Coenradie
 */
@Service("addressService")
@RemotingDestination(channels = {"my-amf"})
public class AddressServiceImpl implements AddressService {

    private List<AddressDTO> addresses = new ArrayList<AddressDTO>();

    private ContactCommandHandler contactCommandHandler;

    @Autowired
    public AddressServiceImpl(ContactCommandHandler contactCommandHandler) {
        this.contactCommandHandler = contactCommandHandler;

        addresses.add(new AddressDTO("serverstraat 1", "", "Amsterdam", "Roberto"));
        addresses.add(new AddressDTO("kerstraat 12", "", "Zoetermeer", "Jettro"));
        addresses.add(new AddressDTO("havenlaan 100", "", "Maassluis", "WasIk"));
        addresses.add(new AddressDTO("kustweg 4", "", "Monster", "Michael"));
        addresses.add(new AddressDTO("axonboulavard nr 1", "", "Leidscheveen", "Allard"));
    }

    @RemotingInclude
    @Override
    public List<AddressDTO> searchAddresses() {
        return Collections.unmodifiableList(addresses);
    }

    @RemotingInclude
    @Override
    public void createAddress(AddressDTO addressDTO) {
        UUID contact = contactCommandHandler.createContact(addressDTO.getContactName());
        Address address = new Address(addressDTO.getStreet(), addressDTO.getZipCode(), addressDTO.getCity());
        contactCommandHandler.registerAddress(contact, AddressType.WORK, address);
    }
}
