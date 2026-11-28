package com.orion.voicelab.brain
enum class ToolRisk { READ_ONLY, REVERSIBLE_WRITE, EXTERNAL_SIDE_EFFECT, DESTRUCTIVE }
enum class ToolAvailability { OPERATIONAL, PARTIAL, REQUIRES_CONFIGURATION, NOT_IMPLEMENTED }
data class ToolCapability(val id:String,val name:String,val operations:Set<String>,val risk:ToolRisk,val availability:ToolAvailability,val limitation:String)
object ToolCapabilityCatalog {
 val tools=listOf(
  ToolCapability("gmail","Gmail",setOf("search_messages","get_message","create_draft"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.PARTIAL,"Lectura y borradores según autenticación; envío no debe asumirse disponible."),
  ToolCapability("github","GitHub",setOf("list_repositories","list_issues","get_issue","list_commits","list_workflow_runs","list_files","get_file","create_branch","commit_changes","create_pull_request"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.PARTIAL,"Inspección y escritura controlada de repositorios; falta conectar workspace, propuestas de cambios y ejecución aislada de compilaciones y pruebas."),
  ToolCapability("google-maps","Google Maps",setOf("search_place","navigate"),ToolRisk.EXTERNAL_SIDE_EFFECT,ToolAvailability.PARTIAL,"Abre intents del sistema; navegación requiere acción del usuario."),
  ToolCapability("pescaia","PescaIA Surfcasting",setOf("search_public"),ToolRisk.READ_ONLY,ToolAvailability.PARTIAL,"Consulta páginas públicas concretas; no accede a cuentas ni todos los datos dinámicos."),
  ToolCapability("calendar","Calendario Android",setOf("read_events","create_event"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.PARTIAL,"Lectura y alta revisada por el usuario; no modifica ni elimina eventos."),
  ToolCapability("local-files","Archivos locales",setOf("read","create_draft"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.REQUIRES_CONFIGURATION,"Falta flujo general de documentos con selector y permisos."),
  ToolCapability("code-sandbox","Entorno de programación aislado",setOf("inspect","edit","build","test"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.NOT_IMPLEMENTED,"No hay sandbox de ejecución integrado en Android."),
  ToolCapability("browser","Navegador de investigación",setOf("search","open","extract","cite"),ToolRisk.READ_ONLY,ToolAvailability.PARTIAL,"Recuperación web HTML acotada; no es navegador completo ni procesa bien contenido dinámico/PDF.")
 )
 fun find(id:String):ToolCapability?=tools.firstOrNull{it.id==id}
}
