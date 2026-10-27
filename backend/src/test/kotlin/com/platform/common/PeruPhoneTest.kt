package com.platform.common

import com.platform.common.validation.PeruPhone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PeruPhoneTest {
    @ParameterizedTest
    @ValueSource(strings = ["987654321", "987 654 321", "987-654-321", "+51987654321", "51987654321", "+51 987 654 321", "0051987654321", "(51) 987654321"])
    fun `normaliza celulares peruanos a E164`(input: String) {
        assertThat(PeruPhone.normalize(input)).isEqualTo("+51987654321")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "12345", "887654321", "9876543210", "+1987654321", "98765432a", "014567890", "+52987654321"])
    fun `rechaza numeros que no son celulares peruanos`(input: String) {
        assertThat(PeruPhone.normalize(input)).isNull()
    }
}
