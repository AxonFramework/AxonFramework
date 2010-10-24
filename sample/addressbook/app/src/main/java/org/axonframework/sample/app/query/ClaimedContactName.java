package org.axonframework.sample.app.query;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * @author Jettro Coenradie
 */
@Entity
public class ClaimedContactName {

    @Id
    private String contactName;

    public ClaimedContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactName() {
        return contactName;
    }

    /**
     * Required for jpa
     */
    public ClaimedContactName() {
    }
}
