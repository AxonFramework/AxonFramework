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

package org.axonframework.examples.addressbook.controllers {
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.UpdatedContactNotificationMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

/**
 * Handles incoming contact updates
 */
public class UpdatedContactController extends BaseController {

    [Inject]
    public var contactModel:ContactModel;

    public function UpdatedContactController() {
        super();
    }

    public function execute(message:UpdatedContactNotificationMessage):void {
        var uuid:String = message.contact.uuid;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = contactModel.findContactByIdentifier(uuid);
        }
        if (contact != null) {
            dispatcher(new NotificationMessage('Received contact update for ' + message.contact.name));
            contact.name = message.contact.name;
        } else {
            dispatcher(new NotificationMessage('Received new contact ' + message.contact.name));
            contactModel.contacts.addItem(message.contact);
        }
    }

}
}