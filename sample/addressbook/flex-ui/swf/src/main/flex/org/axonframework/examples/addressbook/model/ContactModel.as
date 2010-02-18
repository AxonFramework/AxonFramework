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

package org.axonframework.examples.addressbook.model {
import mx.collections.ArrayCollection;

import org.axonframework.examples.addressbook.messages.ContactAddressesMessage;
import org.axonframework.examples.addressbook.messages.ErrorNotificationMessage;

public class ContactModel {

    [MessageDispatcher]
    public var dispatcher:Function;

    [Bindable]
    public var contacts:ArrayCollection = new ArrayCollection();

    [Bindable]
    public var selectedContact:Contact = new Contact();

    public function ContactModel() {
    }

    [MessageHandler]
    public function obtainedAddressForContact(message:ContactAddressesMessage):void {
        var uuid:String = message.contactIdentifier;
        var contact:Contact = findContactByIdentifier(uuid);
        if (contact == null) {
            dispatcher(new ErrorNotificationMessage("Got an address for non existing user "
                    + message.contactIdentifier));
            return;
        }
        contact.addresses = message.addresses;
    }

    public function findContactByIdentifier(identifier:String):Contact {
        for each (var contact:Contact in contacts) {
            if (contact.uuid == identifier) {
                return contact;
            }
        }
        return null;
    }

    public function removeContact(uuid:String):void {
        var i:int = 0;
        while (i < uuid.length) {
            if (contacts.getItemAt(i).uuid == uuid) {
                this.contacts.removeItemAt(i);
            }
            i++;
        }

    }
}
}