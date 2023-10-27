/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.disruptor.commandhandling;

import com.lmax.disruptor.RingBuffer;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;

/**
 * Wrapper for command handler Callbacks that detects blacklisted aggregates and starts a cleanup process when an
 * aggregate is blacklisted.
 *
 * @param <R> The return value of the Command
 * @param <C> The type of payload of the dispatched command
 * @author Allard Buijze
 * @since 2.0
 */
public class BlacklistDetectingCallback<C, R> implements CommandCallback<C, R> {

    private static final Logger logger = LoggerFactory.getLogger(BlacklistDetectingCallback.class);

    private final CommandCallback<? super C, R> delegate;
    private final RingBuffer<CommandHandlingEntry> ringBuffer;
    private final BiConsumer<CommandMessage<? extends C>, CommandCallback<? super C, R>> retryMethod;
    private final boolean rescheduleOnCorruptState;

    /**
     * Initializes the callback which allows the given {@code command} to be rescheduled on the given {@code ringBuffer}
     * if it failed due to a corrupt state.
     *
     * @param delegate                 The callback to invoke when an exception occurred
     * @param ringBuffer               The RingBuffer on which an Aggregate Cleanup should be scheduled when a corrupted
     *                                 aggregate state was detected
     * @param retryMethod              The method to reschedule a command if it was executed on a corrupt aggregate
     * @param rescheduleOnCorruptState Whether the command should be retried if it has been executed against corrupt
     *                                 state
     */
    public BlacklistDetectingCallback(CommandCallback<? super C, R> delegate,
                                      RingBuffer<CommandHandlingEntry> ringBuffer,
                                      BiConsumer<CommandMessage<? extends C>, CommandCallback<? super C, R>> retryMethod,
                                      boolean rescheduleOnCorruptState) {
        this.delegate = delegate;
        this.ringBuffer = ringBuffer;
        this.retryMethod = retryMethod;
        this.rescheduleOnCorruptState = rescheduleOnCorruptState;
    }

    @Override
    public void onResult(@Nonnull CommandMessage<? extends C> commandMessage,
                         @Nonnull CommandResultMessage<? extends R> commandResultMessage) {
        if (!commandResultMessage.isExceptional()) {
            if (delegate != null) {
                delegate.onResult(commandMessage, commandResultMessage);
            }
        } else {
            Throwable cause = commandResultMessage.exceptionResult();
            if (cause instanceof AggregateBlacklistedException) {
                long sequence = ringBuffer.next();
                CommandHandlingEntry event = ringBuffer.get(sequence);
                event.resetAsRecoverEntry(((AggregateBlacklistedException) cause).getAggregateIdentifier());
                ringBuffer.publish(sequence);
                if (delegate != null) {
                    delegate.onResult(commandMessage, asCommandResultMessage(cause.getCause()));
                }
            } else if (rescheduleOnCorruptState && cause instanceof AggregateStateCorruptedException) {
                retryMethod.accept(commandMessage, delegate);
            } else if (delegate != null) {
                delegate.onResult(commandMessage, commandResultMessage);
            } else {
                logger.warn("Command {} resulted in an exception:",
                            commandMessage.getPayloadType().getSimpleName(),
                            cause);
            }
        }
    }

    /**
     * Indicates whether this callback has a delegate that needs to be notified of the command handling result
     *
     * @return {@code true} if this callback has a delegate, otherwise {@code false}.
     */
    public boolean hasDelegate() {
        return delegate != null;
    }
}
