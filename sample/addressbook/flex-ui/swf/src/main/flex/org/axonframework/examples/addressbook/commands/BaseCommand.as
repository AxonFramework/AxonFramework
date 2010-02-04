package org.axonframework.examples.addressbook.commands {
import mx.controls.Alert;
import mx.rpc.Fault;
import mx.rpc.remoting.mxml.RemoteObject;

public class BaseCommand {
    [MessageDispatcher]
    public var dispatcher:Function;

    [Inject(id="remoteAddressService")]
    public var addressService:RemoteObject;


    public function BaseCommand() {
    }

    public function error(fault:Fault):void {
        Alert.show(fault.faultString);
    }


}
}