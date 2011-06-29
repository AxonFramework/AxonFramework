package org.axonframework.examples.addressbook.rest;

/**
 * @author Jettro Coenradie
 */
public class ContactCannotBeFoundException extends RuntimeException {
    public ContactCannotBeFoundException(String s) {
        super("Contact cannot be found : " + s);
    }
}
