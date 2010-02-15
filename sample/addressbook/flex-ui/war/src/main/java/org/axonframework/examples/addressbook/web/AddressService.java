package org.axonframework.examples.addressbook.web;

import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.axonframework.examples.addressbook.web.dto.ContactDTO;
import org.axonframework.sample.app.AddressType;

import java.util.List;

/**
 * @author Jettro Coenradie
 */
public interface AddressService {
    List<AddressDTO> searchAddresses(AddressDTO searchAddress);

    void createAddress(AddressDTO address);

    List<ContactDTO> obtainAllContacts();

    void createContact(ContactDTO contact);

    List<AddressDTO> obtainContactAddresses(String contactIdentifier);

    void removeAddressFor(String contactIdentifier, AddressType addressType);

    void removeContact(String contactIdentifier);
}
