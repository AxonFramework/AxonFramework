package org.axonframework.examples.addressbook.services {
import flash.events.EventDispatcher;

import mx.collections.ArrayCollection;
import mx.controls.Alert;
import mx.rpc.AsyncToken;
import mx.rpc.events.FaultEvent;
import mx.rpc.remoting.mxml.RemoteObject;

import org.axonframework.examples.addressbook.messages.NewAddressMessage;
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.SearchForAddressesMessage;
import org.axonframework.examples.addressbook.messages.SearchResultMessage;

public class AddressServiceRemote extends EventDispatcher implements IAddressService {

    [Inject(id="remoteAddressService")]
    public var addressService:RemoteObject;

    [MessageDispatcher]
    public var dispatcher:Function;


    public function AddressServiceRemote() {
        super();
    }

    /* Searching */

    [Command]
    public function search(message:SearchForAddressesMessage):AsyncToken {
        return addressService.searchAddresses();
    }

    [CommandResult]
    public function searchResult(addresses:ArrayCollection, message:SearchForAddressesMessage):void {
        dispatcher(new SearchResultMessage(addresses));
    }

    [CommandError]
    public function searchError(fault:FaultEvent, trigger:SearchForAddressesMessage):void {
        Alert.show(fault.fault.faultString);
    }

    /* Creating */
    [Command]
    public function create(message:NewAddressMessage):AsyncToken {
        return addressService.createAddress(message.address);
    }

    [CommandResult]
    public function createResult(trigger:NewAddressMessage):void {
        dispatcher(new NotificationMessage("The new address command has been received"));
    }

    [CommandError]
    public function createError(fault:FaultEvent, trigger:NewAddressMessage):void {
        Alert.show(fault.fault.faultString);
    }

}
}