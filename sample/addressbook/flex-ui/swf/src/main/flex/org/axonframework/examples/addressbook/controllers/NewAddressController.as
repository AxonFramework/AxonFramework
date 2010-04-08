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
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.commands.RegisterAddressCommand;
import org.axonframework.examples.addressbook.messages.ValidationMessage;
import org.axonframework.examples.addressbook.messages.command.NewAddressCommandMessage;
import org.axonframework.examples.addressbook.messages.notification.NotificationMessage;
import org.axonframework.examples.addressbook.model.Address;

/**
 * Command that receives the request to create a new address : NewAddressMessage
 */
public class NewAddressController extends BaseController {
    private var address:Address;

    public function NewAddressController() {
        super();
    }

    public function execute(message:NewAddressCommandMessage):AsyncToken {
        if (message.address.city.length < 1) {
            dispatcher(new ValidationMessage("City is required for address"));
            return null;
        }

        if (message.address.street.length < 1) {
            dispatcher(new ValidationMessage("Street is required for address"));
            return null;
        }

        if (message.address.zipCode.length < 1) {
            dispatcher(new ValidationMessage("Zipcode is required for address"));
            return null
        }

        this.address = message.address;

        var registerAddressCommand:RegisterAddressCommand = new RegisterAddressCommand();
        registerAddressCommand.contactId = address.contactUUID;
        registerAddressCommand.streetAndNumber = address.street;
        registerAddressCommand.zipCode = address.zipCode;
        registerAddressCommand.city = address.city;
        registerAddressCommand.addressType = address.type;

        return commandReceiver.sendCommand(registerAddressCommand);
    }

    public function result():void {
        dispatcher(new NotificationMessage("New address for : " + this.address.contactName));
    }

}
}