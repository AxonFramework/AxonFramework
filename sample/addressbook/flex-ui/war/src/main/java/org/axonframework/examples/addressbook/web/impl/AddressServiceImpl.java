package org.axonframework.examples.addressbook.web.impl;

import org.axonframework.examples.addressbook.web.AddressService;
import org.axonframework.examples.addressbook.web.model.Address;
import org.springframework.flex.remoting.RemotingDestination;
import org.springframework.flex.remoting.RemotingInclude;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Jettro Coenradie
 */
@Service("addressService")
@RemotingDestination(channels={"my-amf","my-secure-amf"})
public class AddressServiceImpl implements AddressService {
    private List<Address> addresses = new ArrayList<Address>();

    public AddressServiceImpl() {
        addresses.add(new Address("serverstraat 1", "Amsterdam"));
        addresses.add(new Address("kerstraat 1", "Zoetermeer"));
        addresses.add(new Address("havenlaan 1", "Maassluis"));
        addresses.add(new Address("kustweg 1", "Monster"));
        addresses.add(new Address("axonboulavard 1", "Leidscheveen"));
    }

    @RemotingInclude
    public List<Address> searchAddresses() {
        return Collections.unmodifiableList(addresses);
    }
}
