package org.axonframework.commandhandling;

import java.util.Set;

/**
 * Describes a component capable of specifying which commands it is able to handle.
 *
 * @author Allard Buijze
 */
public interface SupportedCommandNamesAware {

    /**
     * Returns the set of command names this component supports.
     *
     * @return the set of supported command names
     */
    Set<String> supportedCommandNames();

}
