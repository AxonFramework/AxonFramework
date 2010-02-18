/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.examples.addressbook.commands {
import mx.rpc.Fault;
import mx.rpc.remoting.mxml.RemoteObject;

import org.axonframework.examples.addressbook.messages.ErrorNotificationMessage;

/**
 * Parent class for all Command classes. Using this class as a parent, the dispatcher and the remote address service
 * are available. This parent class also provided the default error handling message.
 */
public class BaseCommand {
    [MessageDispatcher]
    public var dispatcher:Function;

    [Inject(id="remoteAddressService")]
    public var addressService:RemoteObject;


    public function BaseCommand() {
        // default constructor
    }

    /**
     * Method to be used as error handler for remote calls. The error is placed into an ErrorNotiificationMessage
     * that is dispatched.
     * @param fault
     */
    public function error(fault:Fault):void {
        dispatcher(new ErrorNotificationMessage(fault.faultString));
    }

}
}