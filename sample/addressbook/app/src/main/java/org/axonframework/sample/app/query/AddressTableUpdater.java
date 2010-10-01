/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.sample.app.query;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.sample.app.AddressAddedEvent;
import org.axonframework.sample.app.AddressChangedEvent;
import org.axonframework.sample.app.AddressRemovedEvent;
import org.axonframework.sample.app.ContactCreatedEvent;
import org.axonframework.sample.app.ContactDeletedEvent;
import org.axonframework.sample.app.ContactNameChangedEvent;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * @author Allard Buijze
 */
public class AddressTableUpdater {

    @PersistenceContext
    private EntityManager entityManager;

    @EventHandler
    public void handleContactCreatedEvent(ContactCreatedEvent event) {
        ContactEntry entry = new ContactEntry();
        entry.setIdentifier(event.getAggregateIdentifier().asString());
        entry.setName(event.getName());
        entityManager.persist(entry);
    }

    @EventHandler
    public void handleContactNameChangedEvent(ContactNameChangedEvent event) {
        entityManager.createQuery("UPDATE ContactEntry e SET e.name = :newName WHERE e.identifier = :id")
                .setParameter("newName", event.getNewName())
                .setParameter("id", event.getAggregateIdentifier().asString())
                .executeUpdate();
        entityManager.createQuery("UPDATE AddressEntry e SET e.name = :newName WHERE e.identifier = :id")
                .setParameter("newName", event.getNewName())
                .setParameter("id", event.getAggregateIdentifier().asString())
                .executeUpdate();
    }

    @EventHandler
    public void handleContactDeletedEvent(ContactDeletedEvent event) {
        entityManager.createQuery("DELETE FROM AddressEntry e WHERE e.identifier = :id")
                .setParameter("id", event.getAggregateIdentifier().asString())
                .executeUpdate();

        entityManager.createQuery("DELETE FROM ContactEntry e WHERE e.identifier = :id")
                .setParameter("id", event.getAggregateIdentifier().asString())
                .executeUpdate();
    }

    @EventHandler
    public void handleAddressDeletedEvent(AddressRemovedEvent event) {
        entityManager.createQuery("DELETE FROM AddressEntry e WHERE e.identifier = :id and e.addressType = :type")
                .setParameter("id", event.getAggregateIdentifier().asString())
                .setParameter("type", event.getType())
                .executeUpdate();
    }

    @EventHandler
    public void handleAddressChangedEvent(AddressChangedEvent event) {
        AddressEntry entry = (AddressEntry) entityManager.createQuery(
                "SELECT e from AddressEntry e WHERE e.identifier = :id and e.addressType = :type")
                .setParameter("id", event.getAggregateIdentifier().asString())
                .setParameter("type", event.getType())
                .getSingleResult();

        entry.setStreetAndNumber(event.getAddress().getStreetAndNumber());
        entry.setZipCode(event.getAddress().getZipCode());
        entry.setCity(event.getAddress().getCity());
        entityManager.persist(entry);
    }

    @EventHandler
    public void handleAddressAddedEvent(AddressAddedEvent event) {
        ContactEntry contact = (ContactEntry)
                entityManager.createQuery("SELECT e from ContactEntry e WHERE e.identifier = :id")
                        .setParameter("id", event.getAggregateIdentifier().asString())
                        .getSingleResult();
        AddressEntry entry = new AddressEntry();
        entry.setIdentifier(event.getAggregateIdentifier());
        entry.setName(contact.getName());
        entry.setAddressType(event.getType());
        entry.setStreetAndNumber(event.getAddress().getStreetAndNumber());
        entry.setCity(event.getAddress().getCity());
        entry.setZipCode(event.getAddress().getZipCode());
        entityManager.persist(entry);
    }

}
