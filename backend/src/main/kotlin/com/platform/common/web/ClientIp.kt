package com.platform.common.web

import jakarta.servlet.http.HttpServletRequest

/**
 * IP del cliente. `server.forward-headers-strategy=native` hace que Tomcat aplique
 * X-Forwarded-For solo desde proxies internos de confianza, por lo que `remoteAddr` ya es fiable.
 */
fun HttpServletRequest.clientIp(): String = remoteAddr ?: "unknown"

fun HttpServletRequest.userAgentTruncated(): String? = getHeader("User-Agent")?.take(255)
