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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.sample.app.AddressType;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * @author Allard Buijze
 */
@Entity
public class AddressEntry {

    @Id
    @GeneratedValue
    private Long db_identifier;

    @Basic
    @Column(length = 36)
    private String identifier;

    @Basic
    private String name;

    @Basic
    @Enumerated(EnumType.STRING)
    private AddressType addressType;

    @Basic
    private String streetAndNumber;

    @Basic
    private String zipCode;

    @Basic
    private String city;

    public AggregateIdentifier getIdentifier() {
        return AggregateIdentifierFactory.fromString(identifier);
    }

    void setIdentifier(AggregateIdentifier identifier) {
        this.identifier = identifier.asString();
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    void setAddressType(AddressType addressType) {
        this.addressType = addressType;
    }

    public String getStreetAndNumber() {
        return streetAndNumber;
    }

    void setStreetAndNumber(String streetAndNumber) {
        this.streetAndNumber = streetAndNumber;
    }

    public String getZipCode() {
        return zipCode;
    }

    void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCity() {
        return city;
    }

    void setCity(String city) {
        this.city = city;
    }
}
