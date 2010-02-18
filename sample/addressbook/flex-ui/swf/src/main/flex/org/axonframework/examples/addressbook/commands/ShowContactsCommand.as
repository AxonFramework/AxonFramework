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

import org.axonframework.examples.addressbook.messages.ShowContactsMessage;
import org.axonframework.examples.addressbook.model.ContactModel;

/**
 * Command obtains all contacts from the server
 */
public class ShowContactsCommand  extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;

    public function ShowContactsCommand() {
        super();
    }

    public function execute(message:ShowContactsMessage):AsyncToken {
        return addressService.obtainAllContacts();
    }

    public function result(contacts:ArrayCollection):void {
        contactModel.contacts = contacts;
    }

}
}