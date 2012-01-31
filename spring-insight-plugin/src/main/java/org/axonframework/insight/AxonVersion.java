package org.axonframework.insight;

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
