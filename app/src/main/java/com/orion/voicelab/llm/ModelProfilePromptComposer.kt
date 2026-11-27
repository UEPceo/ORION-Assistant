package com.orion.voicelab.llm

object ModelProfilePromptComposer {
    fun compose(profile: LanguageModelProfile = LanguageModelProfileCatalog.available(ModelExecutionMode.ON_DEVICE_OFFLINE).first()): String {
        return """
            PERFIL REAL DEL MOTOR DE LENGUAJE:
            Modelo: ${profile.displayName}
            Modo: ${profile.mode}
            Contexto configurado: ${profile.contextTokens} tokens.
            Capacidades declaradas: ${profile.capabilities.joinToString()}.
            Licencia: ${profile.license}.
            Este perfil es ligero. No afirmes que has ejecutado código, abierto archivos, consultado Internet, generado imagen/vídeo o completado tareas externas si no existe una herramienta conectada y un resultado verificable.
            Para problemas complejos, divide el trabajo, utiliza evidencia y herramientas disponibles, y comunica límites de memoria/contexto. No simules razonamiento profundo ni resultados.
        """.trimIndent()
    }
}
