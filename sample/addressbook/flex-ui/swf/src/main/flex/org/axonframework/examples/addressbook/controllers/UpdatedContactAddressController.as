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
import org.axonframework.examples.addressbook.messages.UpdatedContactAddressNotificationMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

/**
 * Handles an incoming contact address updates by updating the model and sending a notification to the user.
 */
public class UpdatedContactAddressController extends BaseController {
    [Inject]
    public var contactModel:ContactModel;

    public function UpdatedContactAddressController() {
        super();
    }

    public function execute(message:UpdatedContactAddressNotificationMessage):void {
        var uuid:String = message.address.contactUUID;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = contactModel.findContactByIdentifier(uuid);
        }
        if (contact != null) {
            dispatcher(new NotificationMessage("Received an address update for " + contact.name));
            contact.addAddress(message.address);
        }
    }

}
}