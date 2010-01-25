package org.axonframework.examples.addressbook.web;

import org.axonframework.examples.addressbook.web.model.Address;

import java.util.List;

/**
 * @author Jettro Coenradie
 */
public interface AddressService {
    List<Address> searchAddresses();
}
