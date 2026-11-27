package com.orion.voicelab.connectors

/**
 * Product catalog shown by the future Conectores screen.
 * These are roadmap entries, not working integrations.
 */
object ConnectorCatalog {
    val planned: List<ConnectorDescriptor> = listOf(
        ConnectorDescriptor(
            id = ConnectorId.GMAIL,
            displayName = "Gmail",
            description = "Buscar, leer, resumir, preparar borradores y enviar correo con autorización.",
            capabilities = setOf(
                ConnectorCapability.SEARCH,
                ConnectorCapability.READ,
                ConnectorCapability.CREATE_DRAFT,
                ConnectorCapability.SEND
            ),
            isImplemented = false
        ),
        ConnectorDescriptor(
            id = ConnectorId.GITHUB,
            displayName = "GitHub",
            description = "Consultar repositorios, archivos, incidencias y pull requests; proponer cambios y comentarios.",
            capabilities = setOf(
                ConnectorCapability.SEARCH,
                ConnectorCapability.READ,
                ConnectorCapability.CREATE,
                ConnectorCapability.UPDATE,
                ConnectorCapability.COMMENT
            ),
            isImplemented = false
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
