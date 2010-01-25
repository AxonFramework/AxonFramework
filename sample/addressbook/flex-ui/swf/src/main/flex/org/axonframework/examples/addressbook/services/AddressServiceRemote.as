package org.axonframework.examples.addressbook.services {
import flash.events.EventDispatcher;

import mx.rpc.AsyncToken;
import mx.rpc.remoting.mxml.RemoteObject;

import org.axonframework.examples.addressbook.model.Address;

public class AddressServiceRemote extends EventDispatcher implements IAddressService {

    [Inject(id="remoteAddressService")]
    public var addressService:RemoteObject;

    public function AddressServiceRemote() {
        super();
    }

    public function search(searchAddress:Address = null):AsyncToken {
        return addressService.searchAddresses();
    }
}
}