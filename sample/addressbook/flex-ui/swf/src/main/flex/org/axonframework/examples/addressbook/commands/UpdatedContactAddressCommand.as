package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.UpdatedContactAddressMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

public class UpdatedContactAddressCommand extends BaseCommand {
    [Inject]
    public var contactModel:ContactModel;

    public function UpdatedContactAddressCommand() {
        super();
    }

    public function execute(message:UpdatedContactAddressMessage):void {
        trace('Received an internal message from the consumer that an address is updated');
        var uuid:String = message.address.contactUUID;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = contactModel.findContactByIdentifier(uuid);
        }
        if (contact != null) {
            trace('updating an address for a contact after receving an event');
            contact.addAddress(message.address);
        } else {
            trace('Received an address for non existing contact with name : ' + message.address.contactName);
        }
    }

}
}