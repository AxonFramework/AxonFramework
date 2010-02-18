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

package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Address;
import org.axonframework.examples.addressbook.model.Contact;

public class RemoveAddressMessage {
    public var address:Address;
    public var contact:Contact;

    public function RemoveAddressMessage(address:Address, contact:Contact) {
        this.address = address;
        this.contact = contact;
    }
}
}