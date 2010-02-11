package org.axonframework.examples.addressbook.messages {
/**
 * Message picket up by the view component to present errors to the user
 */
public class ErrorNotificationMessage {
    public var message:String;

    public function ErrorNotificationMessage(message:String) {
        this.message = message;
    }
}
}