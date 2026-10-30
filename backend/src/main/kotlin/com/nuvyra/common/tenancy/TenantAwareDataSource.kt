package com.nuvyra.common.tenancy

import com.nuvyra.common.config.AppProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DelegatingDataSource
import java.sql.Connection
import javax.sql.DataSource

/**
 * Cada conexión que sale del pool queda con el negocio del hilo actual: con negocio, pasa al rol `nuvyra_app`
 * (sujeto a las políticas de V17) y fija `app.business_id`; sin negocio, vuelve al usuario dueño. Se aplica al
 * tomar la conexión, así una conexión reutilizada nunca conserva el negocio de la petición anterior.
 */
class TenantAwareDataSource(target: DataSource, private val enabled: Boolean) : DelegatingDataSource(target) {
    override fun getConnection(): Connection = apply(super.getConnection())

    override fun getConnection(username: String, password: String): Connection = apply(super.getConnection(username, password))

    private fun apply(connection: Connection): Connection {
        val businessId = TenantContext.get()?.takeIf { enabled }
        try {
            connection.prepareStatement("select set_config('role', ?, false), set_config('app.business_id', ?, false)").use {
                it.setString(1, if (businessId != null) ROLE else "none")
                it.setString(2, businessId?.toString() ?: "")
                it.execute()
            }
        } catch (ex: Exception) {
            connection.close()
            throw ex
        }
        return connection
    }

    companion object {
        const val ROLE = "nuvyra_app"
    }
}

@Configuration(proxyBeanMethods = false)
class TenantDataSourceConfig {
    companion object {
        /** Estático: los post-procesadores se crean antes que el resto de beans. */
        @Bean
        @JvmStatic
        fun tenantAwareDataSourcePostProcessor(properties: ObjectProvider<AppProperties>): BeanPostProcessor = object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any =
                if (bean is DataSource && bean !is TenantAwareDataSource) {
                    TenantAwareDataSource(bean, properties.getObject().security.rowLevelSecurity)
                } else {
                    bean
                }
        }
    }
}
