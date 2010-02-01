package org.axonframework.examples.addressbook.web.dto;

import org.axonframework.sample.app.query.ContactEntry;

import java.io.Serializable;

/**
 * @author Jettro Coenradie
 */
public class ContactDTO implements Serializable {
    private String name;
    private String uuid;

    public ContactDTO() {
    }

    public static ContactDTO createContactDTOFrom(ContactEntry contactEntry) {
        ContactDTO contactDTO = new ContactDTO();
        contactDTO.setName(contactEntry.getName());
        contactDTO.setUuid(contactEntry.getIdentifier().toString());
        return contactDTO;
    }

    public ContactDTO(String name) {
        this();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
