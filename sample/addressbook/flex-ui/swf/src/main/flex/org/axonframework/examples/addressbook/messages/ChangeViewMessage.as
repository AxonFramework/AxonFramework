package org.axonframework.examples.addressbook.messages {
/**
 * Message used to change the view, The ID of the stack to switch to must be provided. Stack id's are available in the
 * ViewConstants component.
 */
public class ChangeViewMessage {
    public var stackId:String;

    public function ChangeViewMessage(stackId:String) {
        this.stackId = stackId;
    }
}
}