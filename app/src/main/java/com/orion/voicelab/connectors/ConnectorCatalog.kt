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
            description = "Enviar notas a Google Keep mediante Android Sharesheet; importar notas compartidas a ORION sigue pendiente.",
            capabilities = setOf(ConnectorCapability.CREATE),
            isImplemented = true
        ),
        ConnectorDescriptor(
            id = ConnectorId.ANDROID_CALENDAR,
            displayName = "Calendario",
            description = "Preparar eventos y transferirlos a la aplicación de calendario para revisión y guardado; lectura de agenda pendiente.",
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
