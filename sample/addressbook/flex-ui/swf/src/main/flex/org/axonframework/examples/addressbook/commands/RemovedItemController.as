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
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.RemovedItemMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

public class RemovedItemController extends BaseController {
    [Inject]
    public var contactModel:ContactModel;

    public function RemovedItemController() {
        super();
    }

    public function execute(message:RemovedItemMessage):void {

        var uuid:String = message.removed.contactIdentifier;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = contactModel.findContactByIdentifier(uuid);
        }
        if (contact != null) {
            if (message.removed.addressType == null || message.removed.addressType.length == 0) {
                contactModel.removeContact(uuid);
                dispatcher(new NotificationMessage("removed the contact " + contact.name));
            } else {
                contact.removeAddress(message.removed.addressType);
                dispatcher(new NotificationMessage(
                        "removed the " + message.removed.addressType + " address for " + contact.name));
            }
        }
    }

}
}