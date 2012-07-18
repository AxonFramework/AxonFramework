package org.axonframework.test.utils;

import org.axonframework.domain.MetaData;

/**
 * Interface towards a mechanism that replicates the behavior of a Command Handling component. The goal of this
 * component is to mimic behavior on the callback.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface CallbackBehavior {

    /**
     * Invoked when the Command Bus receives a Command that is dispatched with a Callback method. The return value of
     * this invocation is used to invoke the callback.
     *
     * @param commandPayload  The payload of the Command Message
     * @param commandMetaData The MetaData of the CommandMessage
     * @return any return value to pass to the callback's onSuccess method.
     *
     * @throws Throwable If the onFailure method of the callback must be invoked
     */
    Object handle(Object commandPayload, MetaData commandMetaData) throws Throwable;
}
