package org.axonframework.examples.addressbook.web;

import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.axonframework.examples.addressbook.web.dto.ContactDTO;

import java.util.List;

/**
 * @author Jettro Coenradie
 */
public interface AddressService {
    List<AddressDTO> searchAddresses(AddressDTO searchAddress);

    void createAddress(AddressDTO address);

    List<ContactDTO> obtainAllContacts();

    void createContact(ContactDTO contact);
}
