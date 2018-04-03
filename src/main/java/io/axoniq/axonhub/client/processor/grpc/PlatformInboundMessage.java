package io.axoniq.axonhub.client.processor.grpc;

import io.axoniq.platform.grpc.PlatformInboundInstruction;

/**
 * Created by Sara Pellegrini on 15/03/2018.
 * sara.pellegrini@gmail.com
 */
public interface PlatformInboundMessage {

    PlatformInboundInstruction instruction();

}
