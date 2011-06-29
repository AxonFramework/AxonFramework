package org.axonframework.examples.addressbook.rest;

/**
 * @author Jettro Coenradie
 */
public class ContactNameAlreadyTakenException extends RuntimeException {
    public ContactNameAlreadyTakenException(String s) {
        super("The choosen contact name has already been taken:" + s);
    }
}
