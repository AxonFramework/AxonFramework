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

[Bindable]
[RemoteClass(alias="org.axonframework.examples.addressbook.web.dto.ContactDTO")]
public class Contact {

    public var name:String;
    public var uuid:String;
    public var detailsLoaded:Boolean;

    public var addresses:ArrayCollection = new ArrayCollection();

    public function Contact() {
    }

    public static function newContact(name:String, uuid:String = ""):Contact {
        var contact:Contact = new Contact();
        contact.name = name;
        contact.uuid = uuid;
        contact.detailsLoaded = false;
        return contact;
    }

    public function addAddress(address:Address):void {
        var i:int = 0;
        while (i < addresses.length) {
            if (addresses.getItemAt(i).type == address.type) {
                this.addresses.removeItemAt(i);
            }
            i++;
        }
        this.addresses.addItem(address);
    }

    public function removeAddress(addressType:String):void {
        var i:int = 0;
        while (i < addresses.length) {
            if (addresses.getItemAt(i).type == addressType) {
                this.addresses.removeItemAt(i);
            }
            i++;
        }
    }
}
}