package org.axonframework.examples.addressbook.model {
[Bindable]
[RemoteClass(alias="org.axonframework.examples.addressbook.web.dto.AddressDTO")]
public class Address {
    public var street:String;
    public var city:String;
    public var zipCode:String;
    public var contactName:String;

    public function Address() {
    }

    public static function newAddress(contactName:String, street:String, zipCode:String, city:String):Address {
        var address:Address = new Address();
        address.contactName = contactName;
        address.street = street;
        address.zipCode = zipCode;
        address.city = city;
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
        return sameCity && sameStreet && sameZipCode && sameContactName;
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