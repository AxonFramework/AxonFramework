package org.axonframework.examples.addressbook.messages {
/**
 * Message used by the client to show notifications
 */
public class NotificationMessage {
    public var message:String;

    public function NotificationMessage(message:String) {
        this.message = message;
    }
}
}