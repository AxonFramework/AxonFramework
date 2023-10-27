package org.axonframework.annotations;

/**
 * Class level annotation used to provide the default {@link QualifiedName} for a message's payload.
 * <p>
 * TODO Having had a break through concerning the MessageHandlerRegistry allowing registration of handlers
 *  and handling components, and MessageHandlingComponents as an implementation of the MessageHandlerRegistry,
 *  we're moving our focus towards what annotations we require to get a similar feel as with AF4,
 *  but not expect the specific type of the Message's payload being handled.
 */
public @interface QualifiedName {

    /**
     *
     */
    String localName = "";
    /**
     *
     */
    String namespace = "";
}
