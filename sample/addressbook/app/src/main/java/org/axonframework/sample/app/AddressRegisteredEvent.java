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

package org.axonframework.sample.app;

import org.axonframework.core.DomainEvent;

import java.util.UUID;

/**
 * @author Allard Buijze
 */
public abstract class AddressRegisteredEvent extends DomainEvent {

    private final AddressType type;
    private final Address address;

    protected AddressRegisteredEvent(AddressType type, Address address) {
        this.type = type;
        this.address = address;
    }

    public UUID getContactIdentifier() {
        return getAggregateIdentifier();
    }

    public AddressType getType() {
        return type;
    }

    public Address getAddress() {
        return address;
    }
}
