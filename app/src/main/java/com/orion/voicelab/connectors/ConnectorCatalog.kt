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
            id = ConnectorId.GOOGLE_CALENDAR,
            displayName = "Google Calendar",
            description = "Consultar disponibilidad y gestionar eventos con confirmación.",
            capabilities = setOf(
                ConnectorCapability.SEARCH,
                ConnectorCapability.READ,
                ConnectorCapability.CREATE,
                ConnectorCapability.UPDATE,
                ConnectorCapability.DELETE
            ),
            isImplemented = false
        ),
        ConnectorDescriptor(
            id = ConnectorId.GOOGLE_DRIVE,
            displayName = "Google Drive",
            description = "Buscar y consultar documentos autorizados.",
            capabilities = setOf(
                ConnectorCapability.SEARCH,
                ConnectorCapability.READ
            ),
            isImplemented = false
        )
    )
}
