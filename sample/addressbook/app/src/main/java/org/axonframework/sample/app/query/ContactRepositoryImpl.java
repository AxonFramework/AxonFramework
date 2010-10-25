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

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

/**
 * @author Allard Buijze
 */
@Repository
@Transactional(readOnly = true)
public class ContactRepositoryImpl implements ContactRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings({"unchecked"})
    @Override
    public List<ContactEntry> findAllContacts() {
        return entityManager.createQuery("SELECT e FROM ContactEntry e")
                .setMaxResults(250)
                .getResultList();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public List<AddressEntry> findAllAddressesForContact(String contactIdentifier) {
        return entityManager.createQuery("SELECT e FROM AddressEntry e WHERE e.identifier = :id")
                .setParameter("id", contactIdentifier)
                .setMaxResults(10)
                .getResultList();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public List<AddressEntry> findAllAddressesInCityForContact(String name, String city) {
        return entityManager.createQuery("SELECT e FROM AddressEntry e WHERE e.name LIKE :name AND e.city LIKE :city")
                .setParameter("name", "%" + (name == null ? "" : name) + "%")
                .setParameter("city", "%" + (city == null ? "" : city) + "%")
                .setMaxResults(250)
                .getResultList();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public ContactEntry loadContactDetails(String contactIdentifier) {
        return (ContactEntry) entityManager.createQuery("SELECT e FROM ContactEntry e WHERE e.identifier = :id")
                .setParameter("id", contactIdentifier)
                .getSingleResult();
    }
}
