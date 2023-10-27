package org.axonframework.command.messaging;

import org.axonframework.messaging.MessageHandler;

public interface CommandHandler extends MessageHandler<CommandMessage, CommandResultMessage> {

}
