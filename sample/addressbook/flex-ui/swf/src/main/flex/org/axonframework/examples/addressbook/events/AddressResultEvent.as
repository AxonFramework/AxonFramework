package org.axonframework.examples.addressbook.events {
import flash.events.Event;

import mx.collections.ArrayCollection;

public class AddressResultEvent extends Event {
    public static const SEARCHRESULTS:String = "searchResults";

    public var results:ArrayCollection;

    public function AddressResultEvent(type:String,results:ArrayCollection) {
        super(type,true,true);
        this.results = results;
    }
}
}