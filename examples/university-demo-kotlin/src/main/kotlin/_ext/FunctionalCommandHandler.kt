package io.axoniq.demo.university._ext

import org.axonframework.commandhandling.*
import org.axonframework.commandhandling.annotations.CommandHandlingMember
import org.axonframework.commandhandling.configuration.CommandHandlingModule.CommandHandlerPhase
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
import java.util.function.Supplier
import kotlin.reflect.KFunction
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.javaMethod


inline fun <reified T : Any> CommandHandlerPhase.functionalHandler(
  function: KFunction<*>,
  instance: T
) = this.commandHandlingComponent { configuration -> FunctionalCommandHandlerComponent(function, configuration, instance) }

fun CommandHandlerPhase.functionalHandler(
  function: KFunction<*>,
) = this.commandHandlingComponent { configuration -> FunctionalCommandHandlerComponent(function, configuration, null) }


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
    // FIXME -> perform all this on function instead of method
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
          "Unable to resolve parameter $i (${parameters[i].getType().getSimpleName()}) in handler ${method.toGenericString()} .", method
        )
      } else {
        if (supportedPayloadType.isAssignableFrom(parameterResolver.supportedPayloadType())) {
          supportedPayloadType = parameterResolver.supportedPayloadType()
        } else if (!parameterResolver.supportedPayloadType().isAssignableFrom(supportedPayloadType)) {
          throw UnsupportedHandlerException(
            "The method ${method.toGenericString()} seems to have parameters that put conflicting requirements on the payload type" +
              " applicable on that method: ${supportedPayloadType} vs ${parameterResolver.supportedPayloadType()}", method
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
  override fun handleSync(message: Message, context: ProcessingContext, target: T?): Any =
    try {
      handle(message, context, target).first().asCompletableFuture().get()?.message()?.payload()!!
    } catch (e: ExecutionException) {
      if (e.cause is Exception) {
        throw e.cause!!
      } else {
        throw e
      }
    }


  override fun handle(
    message: Message,
    context: ProcessingContext,
    target: T?
  ): MessageStream<*> {
    return try {
      val paramValues = resolveParameterValues(Message.addToContext(context, message))
      val result = function.invokeFunctionAuto(instance = target, args = paramValues)
      returnTypeConverter.apply(result)
    } catch (e: Exception) {
      when (e) {
        is InvocationTargetException, is IllegalAccessException -> {
          if (e.cause is java.lang.Exception || e.cause is Error) {
            MessageStream.failed<Message>(e.cause!!)
          } else {
            MessageStream.failed<Message>(
              MessageHandlerInvocationException("Error handling an object of type $messageType", e)
            )
          }
        }

        else -> throw e
      }
    }
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
   * @param message the message to check for.
   * @param processingContext context to check for.
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

class FunctionalCommandHandlerComponent<T : Any>(
  function: KFunction<*>,
  private val instance: T?,
  parameterResolverFactory: ParameterResolverFactory,
  private val messageTypeResolver: MessageTypeResolver,
  private val converter: MessageConverter
) : CommandHandlingComponent {

  private val handlingComponent: SimpleCommandHandlingComponent = SimpleCommandHandlingComponent.create(
    "FunctionalCommandHandlingComponent${function.name}"
  )

  constructor(function: KFunction<*>, configuration: Configuration, instance: T? = null) : this(
    function = function,
    instance = instance,
    parameterResolverFactory = configuration.getComponent(ParameterResolverFactory::class.java),
    messageTypeResolver = configuration.getComponent(MessageTypeResolver::class.java),
    converter = configuration.getComponent(MessageConverter::class.java)
  )


  init {
    if (!function.isTopLevel()) {
      requireNonNull(instance) { "Member functions must be used on object instance, but none was provided." }
    }
    val member = FunctionalCommandMessageHandlingMember<T>(
      function = function,
      messageType = CommandMessage::class.java,
      parameterResolverFactory = parameterResolverFactory,
      returnTypeConverter = Function { resolveToStream(it, ClassBasedMessageTypeResolver()) }
    )
    val payloadType = member.payloadType()
    val qualifiedName = member.unwrap<CommandHandlingMember<*>>(CommandHandlingMember::class.java)
      .map { it.commandName() }
      .filter { it != payloadType.name }
      .map { QualifiedName(it) }
      .orElseGet(Supplier {
        messageTypeResolver.resolve(payloadType)
          .orElse(MessageType(payloadType))
          .qualifiedName()
      })

    handlingComponent.subscribe(
      qualifiedName,
      CommandHandler { command: CommandMessage, ctx: ProcessingContext ->
        val result = member.handle(command.withConvertedPayload(payloadType, converter), ctx, instance)
        result
          .mapMessage<CommandResultMessage> {
            it as? CommandResultMessage ?: GenericCommandResultMessage(it)
          }
          .first()
          .cast<CommandResultMessage>()
      }
    )
  }

  override fun supportedCommands(): Set<QualifiedName> = handlingComponent.supportedCommands()

  override fun handle(
    command: CommandMessage,
    processingContext: ProcessingContext
  ): MessageStream.Single<CommandResultMessage> = handlingComponent.handle(command, processingContext)

}


fun KFunction<*>.invokeFunctionAuto(
  instance: Any?,
  vararg args: Any?
): Any? {
  return if (this.isTopLevel()) {
    this.call(*args)
  } else {
    requireNotNull(instance) { "Instance required for member function ${this.name}" }
    this.call(instance, *args)
  }
}

fun KFunction<*>.isTopLevel(): Boolean {
  return this.instanceParameter == null && this.extensionReceiverParameter == null
}
