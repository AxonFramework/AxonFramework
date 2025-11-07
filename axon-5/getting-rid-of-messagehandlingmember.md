The message handling members currently contain a lot of "magic" involving annotations (ehhh... attributes) and wrapping behavior that triggers on those atrtibutes.

The thing the member essentially does, is invoke a Method (not a constructor anymore), based on a message. It resolves the parameters for the invocation and converts the response value of the message to a CompletableFuture.

Any wrapping that needs to be done based on attributes, or annotations, should be wrapped at the configuration level. In this approach, the AnnotatedMessageHandlerAdapter inspects a class, finds methods, build a MethodInvokingMessageHandler, and allows components to wrap it based on the attributes it found.