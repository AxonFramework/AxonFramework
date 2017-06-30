package org.axonframework.serialization.upcasting.event;

import org.axonframework.serialization.upcasting.MultiEntryUpcaster;

/**
 * Abstract implementation of a {@link MultiEntryUpcaster} and an {@link EventUpcaster} that eases the common process of
 * upcasting one intermediate event representation to several other representations by applying a flat mapping function
 * to the input stream of intermediate representations.
 *
 * @author Steven van Beelen
 */
public abstract class MultiEventUpcaster extends MultiEntryUpcaster<IntermediateEventRepresentation> implements EventUpcaster {
}
