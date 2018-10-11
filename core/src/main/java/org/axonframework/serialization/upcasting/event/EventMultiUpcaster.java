package org.axonframework.serialization.upcasting.event;

import org.axonframework.serialization.upcasting.SingleEntryMultiUpcaster;

/**
 * Abstract implementation of a {@link SingleEntryMultiUpcaster} and an {@link EventUpcaster} that eases the common
 * process of
 * upcasting one intermediate event representation to several other representations by applying a flat mapping function
 * to the input stream of intermediate representations.
 *
 * @author Steven van Beelen
 * @since 3.0.6
 */
public abstract class EventMultiUpcaster
        extends SingleEntryMultiUpcaster<IntermediateEventRepresentation> implements EventUpcaster {

}
