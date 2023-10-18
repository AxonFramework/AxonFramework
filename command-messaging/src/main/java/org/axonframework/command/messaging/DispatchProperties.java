package org.axonframework.command.messaging;

import java.time.Duration;

public interface DispatchProperties {

    Confirmation confirmation();

    /**
     * The amount of time a dispatcher is willing to wait before a command is dispatched. When this window is exceeded,
     * the command is regarded as expired.
     *
     * @return
     */
//    Duration expiry();
//    Duration timeout();

    enum Confirmation {
        DISPATCHED,         // 1.
        HANDLER_RECEIVED,   // 2.
        HANDLER_HANDLED     // 3.
    }

    class FireAndForget implements DispatchProperties {

        @Override
        public Confirmation confirmation() {
            return Confirmation.DISPATCHED;
        }

//        @Override
//        public Duration expiry() {
//            return Duration.ofSeconds(1);
//        }
    }
}
