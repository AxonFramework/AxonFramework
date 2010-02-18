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
[Bindable]
[RemoteClass(alias="org.axonframework.examples.addressbook.web.dto.AddressDTO")]
public class Address {
    public var street:String;
    public var city:String;
    public var zipCode:String;
    public var contactName:String;
    public var contactUUID:String;
    public var type:String;

    public function Address() {
    }

    public static function newAddress(contactName:String, street:String, zipCode:String, city:String, type:String = "work"):Address {
        var address:Address = new Address();
        address.contactName = contactName;
        address.street = street;
        address.zipCode = zipCode;
        address.city = city;
        address.type = type;
        return address;
    }

    public function same(address:Address):Boolean {
        if (address == null) {
            return false;
        }
        var sameCity:Boolean = sameString(city, address.city);
        var sameStreet:Boolean = sameString(street, address.street);
        var sameZipCode:Boolean = sameString(zipCode, address.zipCode);
        var sameContactName:Boolean = sameString(contactName, address.contactName);
        var sameType:Boolean = sameString(type, address.type);
        return sameCity && sameStreet && sameZipCode && sameContactName && sameType;
    }

    private function sameString(base:String, search:String):Boolean {
        var sameString:Boolean = true;
        if (search != "") {
            sameString = base.toLocaleLowerCase().lastIndexOf(search.toLocaleLowerCase()) != -1;
        }

        return sameString;
    }
}
}