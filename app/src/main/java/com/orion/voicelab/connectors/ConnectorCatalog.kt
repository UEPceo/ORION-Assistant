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
            description = "Consultar repositorios, archivos, incidencias, commits y estado de GitHub Actions (solo lectura).",
            capabilities = setOf(
                ConnectorCapability.SEARCH,
                ConnectorCapability.READ
            ),
            isImplemented = true
        ),
        ConnectorDescriptor(
            id = ConnectorId.ANDROID_NOTES,
            displayName = "Notas del móvil",
            description = "Enviar notas a Google Keep mediante Android Sharesheet. La importación entrante y la biblioteca local indexable siguen pendientes.",
            capabilities = setOf(ConnectorCapability.CREATE),
            isImplemented = false
        ),
        ConnectorDescriptor(
            id = ConnectorId.ANDROID_CALENDAR,
            displayName = "Calendario",
            description = "Consultar eventos y disponibilidad; crear o modificar eventos con confirmación y permisos del sistema.",
            capabilities = setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ, ConnectorCapability.CREATE, ConnectorCapability.UPDATE, ConnectorCapability.DELETE),
            isImplemented = false
        ),
        ConnectorDescriptor(
            id = ConnectorId.GOOGLE_MAPS,
            displayName = "Google Maps",
            description = "Buscar lugares, abrir ubicaciones y preparar rutas o navegación mediante Google Maps.",
            capabilities = setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ),
            isImplemented = false
        ),
        ConnectorDescriptor(
            id = ConnectorId.PESCAIA,
            displayName = "PescaIA Surfcasting",
            description = "Consultar playas, previsión marina, mareas, solunar, técnicas y normativa con fuentes y fecha de revisión.",
            capabilities = setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ),
            isImplemented = false
        )
    )
}
