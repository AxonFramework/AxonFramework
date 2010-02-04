package org.axonframework.examples.addressbook.commands {
import mx.controls.Alert;
import mx.rpc.AsyncToken;
import mx.rpc.Fault;
import mx.rpc.remoting.mxml.RemoteObject;

import org.axonframework.examples.addressbook.messages.NewAddressMessage;
import org.axonframework.examples.addressbook.messages.NotificationMessage;

public class NewAddressCommand extends BaseCommand {

    [Inject(id="remoteAddressService")]
    public var addressService:RemoteObject;

    public function NewAddressCommand() {
        super();
    }


    public function execute(message:NewAddressMessage):AsyncToken {
        trace("Create a new address from the received message: **********************");
        // We could implement some validation here
        return addressService.createAddress(message.address);
    }

    public function result():void {
        trace("The new address command has been received");
        dispatcher(new NotificationMessage("The new address command has been received"));
    }

    public function error(fault:Fault):void {
        Alert.show(fault.faultString);
    }
}
}