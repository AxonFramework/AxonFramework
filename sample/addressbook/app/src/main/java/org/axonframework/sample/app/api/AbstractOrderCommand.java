package org.axonframework.sample.app.api;

/**
 * @author Jettro Coenradie
 */
public abstract class AbstractOrderCommand {
    private String contactId;

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

}
