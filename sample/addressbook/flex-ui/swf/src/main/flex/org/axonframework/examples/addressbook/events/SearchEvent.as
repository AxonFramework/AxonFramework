package org.axonframework.examples.addressbook.events {
import flash.events.Event;

import org.axonframework.examples.addressbook.model.Address;

public class SearchEvent extends Event {
    public static const SEARCH:String = "search";

    public var searchAddress:Address;

    public function SearchEvent(type:String, searchAddress:Address = null) {
        trace('New search event created of type : ' + type);
        super(type,true,true);
        this.searchAddress = searchAddress;
    }
}
}