package org.axonframework.examples.addressbook.web.dto;

import org.axonframework.sample.app.AddressType;
import org.axonframework.sample.app.query.AddressEntry;

import java.io.Serializable;

/**
 * @author Jettro Coenradie
 */
public class AddressDTO implements Serializable {

    private String contactName;
    private String contactUUID;

    private AddressType type;
    private String street;
    private String city;
    private String zipCode;

    public AddressDTO() {
    }

    public static AddressDTO createFrom(AddressEntry addressEntry) {
        AddressDTO newAddress = new AddressDTO();
        newAddress.setType(addressEntry.getAddressType());
        newAddress.setCity(addressEntry.getCity());
        newAddress.setContactName(addressEntry.getName());
        newAddress.setStreet(addressEntry.getStreetAndNumber());
        newAddress.setZipCode(addressEntry.getZipCode());
        newAddress.setContactUUID(addressEntry.getIdentifier().toString());

        return newAddress;
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactUUID() {
        return contactUUID;
    }

    public void setContactUUID(String contactUUID) {
        this.contactUUID = contactUUID;
    }

    public AddressType getType() {
        return type;
    }

    public void setType(AddressType type) {
        this.type = type;
    }
}
