package org.axonframework.examples.addressbook.model {
import mx.utils.ObjectUtil;

public class Address {
    private var _street:String;
    private var _city:String;

    public function Address() {
    }

    public static function newAddress(street:String,city:String):Address {
        var address:Address = new Address();
        address.street = street;
        address.city = city;
        return address;
    }

    public function set street(value:String):void {
        _street = value;
    }

    public function set city(value:String):void {
        _city = value;
    }

    public function get street():String {
        return _street;
    }

    public function get city():String {
        return _city;
    }

    public function same(address:Address):Boolean {
        if (address == null) {
            return false;
        }
        var sameCity:Boolean = sameString(_city,address.city);
        var sameStreet:Boolean = sameString(_street,address.street);
        return sameCity && sameStreet;
    }

    private function sameString(base:String,search:String):Boolean {
        var sameString:Boolean = true;
        if (search != "") {
            sameString = base.toLocaleLowerCase().lastIndexOf(search.toLocaleLowerCase()) != -1;
        }

        return sameString;
    }
}
}