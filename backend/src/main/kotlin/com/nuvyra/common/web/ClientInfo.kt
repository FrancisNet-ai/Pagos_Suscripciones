package com.nuvyra.common.web

/** Origen de una petición (dirección IP y navegador), para sesiones, auditoría y límites de intentos. */
data class ClientInfo(val ip: String, val userAgent: String?)
