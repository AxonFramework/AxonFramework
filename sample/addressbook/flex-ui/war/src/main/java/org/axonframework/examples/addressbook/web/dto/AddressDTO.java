package org.axonframework.examples.addressbook.web.dto;

import java.io.Serializable;

/**
 * @author Jettro Coenradie
 */
public class AddressDTO implements Serializable {

    private String contactName;
    private String street;
    private String city;
    private String zipCode;

    public AddressDTO() {
    }

    public AddressDTO(String street, String zipCode, String city, String contactName) {
        this.contactName = contactName;
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
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
}
