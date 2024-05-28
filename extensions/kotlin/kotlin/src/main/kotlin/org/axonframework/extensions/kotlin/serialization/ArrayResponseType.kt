package org.axonframework.extensions.kotlin.serialization

import org.axonframework.common.ReflectionUtils
import org.axonframework.common.TypeReflectionUtils
import org.axonframework.messaging.responsetypes.AbstractResponseType
import org.axonframework.messaging.responsetypes.InstanceResponseType
import org.axonframework.messaging.responsetypes.ResponseType
import java.lang.reflect.Type
import java.util.concurrent.Future

/**
 * A [ResponseType] implementation that will match with query handlers which return a multiple instances of the
 * expected response type. If matching succeeds, the [ResponseType.convert] function will be called, which
 * will cast the query handler it's response to an [Array] with element type [E].
 *
 * @param E The element type which will be matched against and converted to
 * @author Gerard de Leeuw
 * @see org.axonframework.messaging.responsetypes.MultipleInstancesResponseType
 */
class ArrayResponseType<E>(elementType: Class<E>) : AbstractResponseType<Array<E>>(elementType) {

    companion object {
        /**
         * Indicates that the response matches with the [Type] while returning an iterable result.
         *
         * @see ResponseType.MATCH
         *
         * @see ResponseType.NO_MATCH
         */
        const val ITERABLE_MATCH = 1024
    }

    private val instanceResponseType: InstanceResponseType<E> = InstanceResponseType(elementType)

    /**
     * Match the query handler's response [Type] with this implementation's [E].
     * Will return true in the following scenarios:
     *
     *  * If the response type is an [Array]
     *  * If the response type is a [E]
     *
     * If there is no match at all, it will return false to indicate a non-match.
     *
     * @param responseType the response [Type] of the query handler which is matched against
     * @return true for [Array] or [E] and [ResponseType.NO_MATCH] for non-matches
     */
    override fun matches(responseType: Type): Boolean =
        matchRank(responseType) > NO_MATCH

    /**
     * Match the query handler's response [Type] with this implementation's [E].
     * Will return a value greater than 0 in the following scenarios:
     *
     *  * [ITERABLE_MATCH]: If the response type is an [Array]
     *  * [ResponseType.MATCH]: If the response type is a [E]
     *
     * If there is no match at all, it will return [ResponseType.NO_MATCH] to indicate a non-match.
     *
     * @param responseType the response [Type] of the query handler which is matched against
     * @return [ITERABLE_MATCH] for [Array], [ResponseType.MATCH] for [E] and [ResponseType.NO_MATCH] for non-matches
     */
    override fun matchRank(responseType: Type): Int = when {
        isMatchingArray(responseType) -> ITERABLE_MATCH
        else -> instanceResponseType.matchRank(responseType)
    }

    /**
     * Converts the given [response] of type [Object] into an [Array] with element type [E] from
     * this [ResponseType] instance. Should only be called if [ResponseType.matches] returns true.
     * Will throw an [IllegalArgumentException] if the given response
     * is not convertible to an [Array] of the expected response type.
     *
     * @param response the [Object] to convert into an [Array] with element type [E]
     * @return an [Array] with element type [E], based on the given [response]
     */
    override fun convert(response: Any): Array<E> {
        val responseType: Class<*> = response.javaClass
        if (responseType.isArray) {
            @Suppress("UNCHECKED_CAST")
            return response as Array<E>
        }
        throw IllegalArgumentException(
            "Retrieved response [$responseType] is not convertible to an array with the expected element type [$expectedResponseType]"
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun responseMessagePayloadType(): Class<Array<E>> =
        Array::class.java as Class<Array<E>>

    override fun toString(): String = "ArrayResponseType[$expectedResponseType]"

    private fun isMatchingArray(responseType: Type): Boolean {
        val unwrapped = ReflectionUtils.unwrapIfType(responseType, Future::class.java)
        val iterableType = TypeReflectionUtils.getExactSuperType(unwrapped, Array::class.java)
        return iterableType != null && isParameterizedTypeOfExpectedType(iterableType)
    }
}
