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
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NewContactMessage;
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.ValidationMessage;
import org.axonframework.examples.addressbook.model.Contact;

/**
 * Command that makes use of the received NewContactMessage mesage to create a new Contact
 */
public class NewContactCommand extends BaseCommand {
    private var contact:Contact;

    public function NewContactCommand() {
        super();
    }

    public function execute(message:NewContactMessage):AsyncToken {
        if (message.contact.name.length < 1) {
            dispatcher(new ValidationMessage("Name field is required for contact"));
            return null;
        }
        this.contact = message.contact;
        return addressService.createContact(this.contact);
    }

    public function result():void {
        dispatcher(new NotificationMessage("New contact : " + contact.name));
    }

}
}