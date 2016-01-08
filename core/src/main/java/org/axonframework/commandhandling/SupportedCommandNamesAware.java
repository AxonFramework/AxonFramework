package org.axonframework.commandhandling;

import java.util.Set;

/**
 * @author Allard Buijze
 */
public interface SupportedCommandNamesAware {

    Set<String> supportedCommandNames();

}
