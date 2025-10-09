package io.axoniq.demo.university._ext

import jakarta.annotation.Nonnull
import org.axonframework.commandhandling.*
import org.axonframework.commandhandling.annotations.CommandHandlingMember
import org.axonframework.configuration.Configuration
import org.axonframework.messaging.*
import org.axonframework.messaging.Message
import org.axonframework.messaging.annotations.*
import org.axonframework.messaging.annotations.MessageStreamResolverUtils.resolveToStream
import org.axonframework.messaging.conversion.MessageConverter
import org.axonframework.messaging.unitofwork.ProcessingContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.*
import java.util.Objects.requireNonNull
import java.util.concurrent.ExecutionException
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod


class FunctionalCommandMessageHandlingMember<T : Any>(
  val function: KFunction<*>,
  val messageType: Class<out Message>,
  val parameterResolverFactory: ParameterResolverFactory,
  val returnTypeConverter: Function<Any?, MessageStream<*>>
) : MessageHandlingMember<T> {

  val parameterCount: Int
  val resolvers: Array<ParameterResolver<*>>
  val payloadType: Class<*>
  val method: Method

  init {
    require(function.parameters.isNotEmpty()) { "The command handler must receive at least a command parameter" }
    this.method = requireNotNull(function.javaMethod) { "Kotlin function ${function.name} must correspond to a java method" }
    val parameters: Array<Parameter> = method.parameters
    this.parameterCount = method.parameterCount
    val parameterResolvers: Array<ParameterResolver<*>?> = arrayOfNulls<ParameterResolver<*>>(parameterCount)

    var supportedPayloadType: Class<*> = parameters[0].type
    for (i in 0..<parameterCount) {
      val parameterResolver = parameterResolverFactory.createInstance(method, parameters, i)
      parameterResolvers[i] = parameterResolver
      if (parameterResolver == null) {
        throw UnsupportedHandlerException(
          "Unable to resolve parameter " + i + " (" + parameters[i].getType().getSimpleName() +
            ") in handler " + method.toGenericString() + ".", method
        )
      } else {
        if (supportedPayloadType.isAssignableFrom(parameterResolver.supportedPayloadType())) {
          supportedPayloadType = parameterResolver.supportedPayloadType()
        } else if (!parameterResolver.supportedPayloadType().isAssignableFrom(supportedPayloadType)) {
          throw UnsupportedHandlerException(
            String.format(
              "The method %s seems to have parameters that put conflicting requirements on the payload type" +
                " applicable on that method: %s vs %s", method.toGenericString(),
              supportedPayloadType, parameterResolver.supportedPayloadType()
            ), method
          )
        }
      }
    }
    this.payloadType = supportedPayloadType
    this.resolvers = parameterResolvers.filterNotNull().toTypedArray()
  }

  override fun payloadType(): Class<*> = payloadType

  override fun canHandle(message: Message, context: ProcessingContext): Boolean {
    val contextWithMessage = Message.addToContext(context, message)
    return typeMatches(message)
      && payloadType.isAssignableFrom(message.payloadType())
      && parametersMatch(message, contextWithMessage)
  }

  override fun canHandleMessageType(messageType: Class<out Message>): Boolean {
    return this.payloadType.isAssignableFrom(payloadType)
  }

  @Deprecated("left over from sync version")
  override fun handleSync(
    message: Message,
    context: ProcessingContext,
    target: T?
  ): Any {
    try {
      val resultEntry: MessageStream.Entry<*>? = handle(message, context, target).first()
        .asCompletableFuture()
        .get()
      return (resultEntry?.message()?.payload())!!
    } catch (e: ExecutionException) {
      if (e.cause is Exception) {
        throw e.cause!!
      } else {
        throw e
      }
    }
  }

  override fun handle(
    message: Message,
    context: ProcessingContext,
    target: T?
  ): MessageStream<*> {
    val contextWithMessage = Message.addToContext(context, message)
    val invocationResult: Any?
    try {
      val paramValues = resolveParameterValues(contextWithMessage)
      invocationResult = method.invoke(target, *paramValues)
    } catch (e: IllegalAccessException) {
      if (e.cause is java.lang.Exception) {
        return MessageStream.failed<Message?>(e.cause!!)
      } else if (e.cause is Error) {
        return MessageStream.failed<Message?>(e.cause!!)
      }
      return MessageStream.failed<Message?>(
        MessageHandlerInvocationException(
          String.format(
            "Error handling an object of type [%s]",
            messageType
          ), e
        )
      )
    } catch (e: InvocationTargetException) {
      if (e.cause is java.lang.Exception) {
        return MessageStream.failed<Message>(e.cause!!)
      } else if (e.cause is Error) {
        return MessageStream.failed<Message>(e.cause!!)
      }
      return MessageStream.failed<Message>(
        MessageHandlerInvocationException(
          String.format(
            "Error handling an object of type [%s]",
            messageType
          ), e
        )
      )
    }
    return returnTypeConverter.apply(invocationResult)
  }

  private fun resolveParameterValues(context: ProcessingContext): Array<Any?> {
    val params = arrayOfNulls<Any>(parameterCount)
    for (i in 0..<parameterCount) {
      params[i] = resolvers[i].resolveParameterValue(context)
    }
    return params
  }

  @Suppress("UNCHECKED_CAST")
  override fun <HT : Any> unwrap(handlerType: Class<HT>): Optional<HT> {
    if (handlerType.isInstance(this)) {
      return Optional.of<FunctionalCommandMessageHandlingMember<T>>(this) as Optional<HT>
    }
    if (handlerType.isInstance(method)) {
      return Optional.of<Method>(method) as Optional<HT>
    }
    return Optional.empty<HT>()
  }

  /**
   * Checks if this member can handle the type of the given `message`. This method does not check if the
   * parameter resolvers of this member are compatible with the given message. Use
   * [.parametersMatch] for that.
   *
   * @param message the message to check for
   * @return `true` if this member can handle the message type. `false` otherwise
   */
  fun typeMatches(message: Message): Boolean = messageType.isInstance(message)


  /**
   * Checks if the parameter resolvers of this member are compatible with the given `message` and `context`.
   *
   * @param message the message to check for
   * @return `true` if the parameter resolvers can handle this message and context. `false` otherwise
   */
  fun parametersMatch(message: Message, processingContext: ProcessingContext): Boolean {
    for (resolver in resolvers) {
      if (!resolver.matches(processingContext)) {
        return false
      }
    }
    return true
  }

}

class FunctionalCommandHandlerComponent<T: Any>(
  function: KFunction<*>,
  parameterResolverFactory: ParameterResolverFactory,
  private val messageTypeResolver: MessageTypeResolver,
  private val converter: MessageConverter,
  private val instance: T?
) : CommandHandlingComponent {

  private val handlingComponent: SimpleCommandHandlingComponent = SimpleCommandHandlingComponent.create(
    "FunctionalCommandHandlingComponent${function.name}"
  )

  init {
    if (function.javaMethod != null) {
      requireNonNull(instance) { "Member functions must be used on object instance, but none was provided."}
    }
    val member = FunctionalCommandMessageHandlingMember<T>(
      function = function,
      messageType = CommandMessage::class.java,
      parameterResolverFactory = parameterResolverFactory,
      returnTypeConverter = Function { resolveToStream(it, ClassBasedMessageTypeResolver()) }
    )
    registerHandler(member)
  }

  constructor(function: KFunction<*>, instance: T?, configuration: Configuration) : this(
    function = function,
    instance = instance,
    parameterResolverFactory = configuration.getComponent(ParameterResolverFactory::class.java),
    messageTypeResolver = configuration.getComponent(MessageTypeResolver::class.java),
    converter = configuration.getComponent(MessageConverter::class.java)
  )

  override fun supportedCommands(): Set<QualifiedName> = handlingComponent.supportedCommands()

  override fun handle(
    command: CommandMessage,
    processingContext: ProcessingContext
  ): MessageStream.Single<CommandResultMessage> = handlingComponent.handle(command, processingContext)


  private fun registerHandler(handler: MessageHandlingMember<T>) {
    val payloadType = handler.payloadType()
    val qualifiedName = handler.unwrap<CommandHandlingMember<*>>(CommandHandlingMember::class.java)
      .map<String>(Function { obj: CommandHandlingMember<*> -> obj.commandName() }) // Only use names as is that not match the fully qualified class name.
      .filter(Predicate { name: String -> name != payloadType.getName() })
      .map<QualifiedName>(Function { name: String -> QualifiedName(name) })
      .orElseGet(Supplier {
        messageTypeResolver.resolve(payloadType)
          .orElse(MessageType(payloadType))
          .qualifiedName()
      })

    handlingComponent.subscribe(
      qualifiedName,
      CommandHandler { command: CommandMessage, ctx: ProcessingContext ->
        val result = handler.handle(command.withConvertedPayload(payloadType, converter), ctx, instance)
        result
          .mapMessage<CommandResultMessage> { commandResult: Message -> this.asCommandResultMessage(commandResult) }
          .first()
          .cast<CommandResultMessage>()
      }
    )
  }

  private fun asCommandResultMessage(@Nonnull commandResult: Message) = commandResult as? CommandResultMessage ?: GenericCommandResultMessage(commandResult)

}
