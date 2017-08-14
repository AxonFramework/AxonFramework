package org.axonframework.serialization.upcasting.event;

import org.axonframework.serialization.upcasting.ContextAwareSingleEntryMultiUpcaster;
import org.axonframework.serialization.upcasting.SingleEntryMultiUpcaster;

/**
 * Abstract implementation of a {@link SingleEntryMultiUpcaster} and an {@link EventUpcaster} that eases the common
 * process of upcasting one intermediate event representation to several other representations by applying a flat
 * mapping function to the input stream of intermediate representations.
 * Additionally, it's a context aware implementation, which enables it to store and reuse context information from one
 * entry to another during upcasting.
 *
 * @param <C> the type of context used as {@code C}
 *
 * @author Steven van Beelen
 */
public abstract class ContextAwareEventMultiUpcaster<C> extends ContextAwareSingleEntryMultiUpcaster<IntermediateEventRepresentation, C> implements EventUpcaster {
}
