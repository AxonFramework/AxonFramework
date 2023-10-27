package org.axonframework.messaging;

/**
 * @param <M>
 * @param <R>
 * @param <H>
 * @author Allard Buijze
 * @author Gerard Klijs
 * @author Milan Savic
 * @author Mitchell Herrijgers
 * @author Sara Pellegrini
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface MessageHandlingComponent<M extends Message, R, H extends MessageHandler<M, R>>
        extends MessageHandlerRegistry<M, R, H>, MessageHandler<M, R> {

    String name();
}
