/**
 * Created by IntelliJ IDEA.
 * User: jcoenradie
 * Date: 23-10-10
 * Time: 10:53
 * To change this template use File | Settings | File Templates.
 */
package org.axonframework.examples.addressbook.controllers {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.commands.ChangeContactNameCommand;
import org.axonframework.examples.addressbook.messages.ValidationMessage;
import org.axonframework.examples.addressbook.messages.command.ChangeContactNameCommandMessage;
import org.axonframework.examples.addressbook.messages.notification.NotificationMessage;
import org.axonframework.examples.addressbook.model.Contact;

public class ChangeContactController extends BaseController {
    private var contact:Contact;

    public function ChangeContactController() {
        super();
    }

    public function execute(message:ChangeContactNameCommandMessage):AsyncToken {
        if (message.contact.name.length < 1) {
            dispatcher(new ValidationMessage("Name field is required for contact"));
            return null;
        }
        this.contact = message.contact;
        var changeContactCommand:ChangeContactNameCommand = new ChangeContactNameCommand();
        changeContactCommand.contactNewName = message.contact.name;
        changeContactCommand.contactId = message.contact.uuid;
        return commandReceiver.sendCommand(changeContactCommand);
    }

    public function result():void {
        dispatcher(new NotificationMessage("Name contact updated : " + contact.name));
    }

}
}
