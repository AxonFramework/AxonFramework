package org.axonframework.insight;

/**
 * Used to check for Axon 1.x or higher version being used.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public class AxonVersion {

    static boolean IS_AXON_1X;

    static {
        try {
            Class.forName("org.axonframework.domain.Event");
            IS_AXON_1X = true;
        } catch (ClassNotFoundException e) {
            IS_AXON_1X = false;
        }
    }
}
