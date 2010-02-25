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

package org.axonframework.examples.addressbook.commands {
import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.SelectContactCommandMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

/**
 * Command class that is used to obtain addresses (or details) from a selected contact. Based on the provided contact
 * uuid or identifier we obtain the details. Whether the details need to be obtained from the server of from the cache
 * depends on the "details loaded" flag.
 *
 * If the details are already obtained from the server, the cached details are used. Updates to the details are pushed
 * by the server.
 */
public class SelectContactController extends BaseController {

    [Inject]
    public var contactModel:ContactModel;

    private var findAddressesFor:Contact;

    public function SelectContactController() {
        super();
    }

    public function execute(message:SelectContactCommandMessage):AsyncToken {
        var cachedContact:Contact = contactModel.findContactByIdentifier(message.contact.uuid);
        contactModel.selectedContact = cachedContact;
        findAddressesFor = cachedContact;

        if (!cachedContact.detailsLoaded) {
            return addressService.obtainContactAddresses(message.contact.uuid);
        }
        return null;
    }

    public function result(addresses:ArrayCollection):void {
        var cachedContact:Contact = contactModel.findContactByIdentifier(findAddressesFor.uuid);
        cachedContact.addresses = addresses;
        cachedContact.detailsLoaded = true;
    }
}
}