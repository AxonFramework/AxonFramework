package org.axonframework.examples.addressbook.web.dto;

import org.axonframework.sample.app.AddressType;

import java.io.Serializable;

/**
 * @author Jettro Coenradie
 */
public class RemovedDTO implements Serializable {
    private String contactIdentifier;
    private AddressType addressType;

    public RemovedDTO() {
    }

    public static RemovedDTO createRemovedFrom(String contactIdentifier) {
        RemovedDTO removed = new RemovedDTO();
        removed.setContactIdentifier(contactIdentifier);
        return removed;
    }

    public static RemovedDTO createRemovedFrom(String contactIdentifier, AddressType addressType) {
        RemovedDTO removed = new RemovedDTO();
        removed.setContactIdentifier(contactIdentifier);
        removed.setAddressType(addressType);
        return removed;
    }


    public AddressType getAddressType() {
        return addressType;
    }

    public void setAddressType(AddressType addressType) {
        this.addressType = addressType;
    }

    public String getContactIdentifier() {
        return contactIdentifier;
    }

    public void setContactIdentifier(String contactIdentifier) {
        this.contactIdentifier = contactIdentifier;
    }
}
