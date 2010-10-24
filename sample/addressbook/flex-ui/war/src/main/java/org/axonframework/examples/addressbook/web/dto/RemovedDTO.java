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

package org.axonframework.examples.addressbook.web.dto;

import org.axonframework.sample.app.api.AddressType;

import java.io.Serializable;

/**
 * @author Jettro Coenradie
 */
public class RemovedDTO implements Serializable {
    private String contactIdentifier;
    private AddressType addressType;

    public RemovedDTO() {
    }

    public static RemovedDTO createRemovedFrom(String contactIdentifier) {
        RemovedDTO removed = new RemovedDTO();
        removed.setContactIdentifier(contactIdentifier);
        return removed;
    }

    public static RemovedDTO createRemovedFrom(String contactIdentifier, AddressType addressType) {
        RemovedDTO removed = new RemovedDTO();
        removed.setContactIdentifier(contactIdentifier);
        removed.setAddressType(addressType);
        return removed;
    }


    public AddressType getAddressType() {
        return addressType;
    }

    public void setAddressType(AddressType addressType) {
        this.addressType = addressType;
    }

    public String getContactIdentifier() {
        return contactIdentifier;
    }

    public void setContactIdentifier(String contactIdentifier) {
        this.contactIdentifier = contactIdentifier;
    }
}
