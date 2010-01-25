package org.axonframework.examples.addressbook.web.model;

/**
 * @author Jettro Coenradie
 */
public class Address {
    private String street;
    private String city;

    public Address() {
    }

    public Address(String street, String city) {
        this();
        this.street = street;
        this.city = city;
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
}
