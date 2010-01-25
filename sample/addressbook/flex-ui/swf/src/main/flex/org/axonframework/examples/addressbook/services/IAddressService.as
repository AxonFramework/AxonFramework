package org.axonframework.examples.addressbook.services {
import mx.collections.ArrayCollection;

import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.model.Address;

public interface IAddressService {
    function search(searchAddress:Address = null):AsyncToken;
}
}