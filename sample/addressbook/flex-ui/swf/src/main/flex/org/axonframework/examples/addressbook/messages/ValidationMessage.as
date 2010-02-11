package org.axonframework.examples.addressbook.messages {
/**
 * Message used to send back validation problems to the user
 */
public class ValidationMessage {
    public var message:String;

    public function ValidationMessage(message:String) {
        this.message = message;
    }
}
}