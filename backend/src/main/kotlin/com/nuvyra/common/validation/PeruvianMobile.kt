package com.nuvyra.common.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** Normaliza celulares peruanos al formato internacional E.164 (`+51987654321`). */
object PeruPhone {
    private val separators = "[\\s\\-().]".toRegex()

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        var digits = raw.replace(separators, "")
        if (digits.startsWith("+")) digits = digits.substring(1)
        if (digits.startsWith("0051")) digits = digits.substring(4)
        if (!digits.all { it.isDigit() }) return null
        if (digits.length == 11 && digits.startsWith("51")) digits = digits.substring(2)
        return if (digits.length == 9 && digits.startsWith("9")) "+51$digits" else null
    }

    fun isValid(raw: String?): Boolean = normalize(raw) != null
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PeruvianMobileValidator::class])
annotation class PeruvianMobile(
    val message: String = "Ingresa un celular peruano válido de 9 dígitos",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class PeruvianMobileValidator : ConstraintValidator<PeruvianMobile, String?> {
    // Los nulos se validan con @NotBlank cuando el campo es obligatorio.
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || PeruPhone.isValid(value)
}
