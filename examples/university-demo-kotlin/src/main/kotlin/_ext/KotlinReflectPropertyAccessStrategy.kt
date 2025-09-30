package io.axoniq.demo.university._ext

import com.fasterxml.jackson.module.kotlin.isKotlinClass
import org.axonframework.common.property.Property
import org.axonframework.common.property.PropertyAccessStrategy
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Property access strategy for fancy getter names of the inherited properties.
 */
class KotlinReflectPropertyAccessStrategy : PropertyAccessStrategy() {
  companion object {
    @JvmStatic
    fun <T : Any?> kProperty(targetClass: Class<out T>?, property: String): KProperty1<out T, *>? = if (targetClass != null && targetClass.isKotlinClass()) {
      targetClass.kotlin.memberProperties.singleOrNull { it.name == property }
    } else {
      null
    }

    data class KotlinReflectProperty<T>(val kProperty: KProperty1<out T, *>) : Property<T> {

      @Suppress("UNCHECKED_CAST")
      override fun <V : Any?> getValue(target: T): V = kProperty.call(target) as V
    }
  }

  override fun getPriority(): Int = 1

  override fun <T : Any?> propertyFor(targetClass: Class<out T>?, property: String): Property<T>? = when (val kProperty = kProperty(targetClass, property)) {
    null -> null
    else -> KotlinReflectProperty(kProperty)
  }
}
