package org.axonframework.test.utils;

import org.axonframework.domain.MetaData;

/**
 * Default implementation of the CallbackBehavior interface. This implementation always returns <code>null</code>,
 * which results in the {@link org.axonframework.commandhandling.CommandCallback#onSuccess(Object)} method to be
 * invoked with a <code>null</code> result parameter.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultCallbackBehavior implements CallbackBehavior {

    @Override
    public Object handle(Object commandPayload, MetaData commandMetaData) throws Throwable {
        return null;
    }
}
