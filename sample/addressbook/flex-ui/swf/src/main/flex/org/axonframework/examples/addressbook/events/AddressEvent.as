package org.axonframework.examples.addressbook.events {
import flash.events.Event;

import org.axonframework.examples.addressbook.model.Address;

public class AddressEvent extends Event {
    public static const ADD:String = "addAddress";

    public var address:Address;

    public function AddressEvent(type:String) {
        super(type,true,true);
    }
}
}