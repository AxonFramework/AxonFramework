package org.axonframework.sample.app.api;

/**
 * @author Jettro Coenradie
 */
public class ContactNameAlreadyTakenException extends RuntimeException {
    public ContactNameAlreadyTakenException(String newContactName) {
        super(newContactName);
    }
}
