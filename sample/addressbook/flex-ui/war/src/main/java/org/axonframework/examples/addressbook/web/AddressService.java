package org.axonframework.examples.addressbook.web;

import org.axonframework.examples.addressbook.web.dto.AddressDTO;

import java.util.List;

/**
 * @author Jettro Coenradie
 */
public interface AddressService {
    List<AddressDTO> searchAddresses();

    void createAddress(AddressDTO address);
}
