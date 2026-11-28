package com.orion.voicelab.connectors

/** Product catalog; implemented flags reflect registered provider capabilities. */
object ConnectorCatalog {
    val planned: List<ConnectorDescriptor> = listOf(
        ConnectorDescriptor(
            id = ConnectorId.GMAIL,
            displayName = "Gmail",
            description = "Buscar correos y leer mensajes autorizados en modo solo lectura.",
            capabilities = setOf(
                ConnectorCapability.SEARCH,
                ConnectorCapability.READ
            ),
            isImplemented = true
        ),
        ConnectorDescriptor(
            id = ConnectorId.GITHUB,
            displayName = "GitHub",
            description = "Inspeccionar repositorios, crear ramas, aplicar cambios de código y abrir pull requests con confirmación.",
            capabilities = setOf(
                ConnectorCapability.SEARCH,
                ConnectorCapability.READ,
                ConnectorCapability.CREATE,
                ConnectorCapability.UPDATE,
                ConnectorCapability.COMMENT
            ),
            isImplemented = true
        ),
        ConnectorDescriptor(
            id = ConnectorId.ANDROID_NOTES,
            displayName = "Notas del móvil",
            description = "Enviar notas a Google Keep mediante Android Sharesheet; importar notas compartidas a ORION sigue pendiente.",
            capabilities = setOf(ConnectorCapability.CREATE),
            isImplemented = true
        ),
        ConnectorDescriptor(
            id = ConnectorId.ANDROID_CALENDAR,
            displayName = "Calendario",
            description = "Consultar próximos eventos con permiso explícito y preparar eventos para revisión y guardado en la app de calendario.",
            capabilities = setOf(ConnectorCapability.CREATE),
            isImplemented = true
        ),
        ConnectorDescriptor(
            id = ConnectorId.GOOGLE_MAPS,
            displayName = "Google Maps",
            description = "Buscar lugares y abrir navegación mediante intents de Android.",
            capabilities = setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ),
            isImplemented = true
        ),
        ConnectorDescriptor(
            id = ConnectorId.PESCAIA,
            displayName = "PescaIA Surfcasting",
            description = "Consultar contenido público de PescaIA sobre playas y metodología; las páginas dinámicas y la cuenta privada requieren API específica.",
            capabilities = setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ),
            isImplemented = true
        )
    )
}
