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

import java.util.List;

/**
 * <p>Specification for the query repository implementations. The repository should make it possible to find all
 * contacts and find addresses for a contact or in a certain city.</p>
 *
 * @author Allard Buijze
 */
public interface ContactRepository {

    /**
     * Returns a list with all the contacts
     *
     * @return List of contacts
     */
    List<ContactEntry> findAllContacts();

    /**
     * Returns a list of addresses for the contact with the specified contact identifier
     *
     * @param contactId UUID of the contact to find addresses for
     * @return List of found addresses for the contact
     */
    List<AddressEntry> findAllAddressesForContact(String contactId);

    /**
     * Returns a list of addresses for the specified city and or contact name. If one of the provided parameters is
     * null, it is not used for the query.
     *
     * @param name String representing the name of the contact
     * @param city String representing the city of an address of the contact
     * @return List containing the found addresses
     */
    List<AddressEntry> findAllAddressesInCityForContact(String name, String city);

    /**
     * Returns the contact details for the contact with the provided UUID
     *
     * @param contactId UUID required field containing the contact identifier
     * @return Contact belonging to the provided identifier
     */
    ContactEntry loadContactDetails(String contactId);
}
