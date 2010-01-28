package org.axonframework.examples.addressbook.web.impl;

import org.axonframework.examples.addressbook.web.AddressService;
import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.axonframework.sample.core.Address;
import org.axonframework.sample.core.AddressType;
import org.axonframework.sample.core.command.ContactCommandHandler;
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
