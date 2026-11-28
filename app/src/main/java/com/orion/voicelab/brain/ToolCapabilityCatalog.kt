package com.orion.voicelab.brain
enum class ToolRisk { READ_ONLY, REVERSIBLE_WRITE, EXTERNAL_SIDE_EFFECT, DESTRUCTIVE }
enum class ToolAvailability { OPERATIONAL, PARTIAL, REQUIRES_CONFIGURATION, NOT_IMPLEMENTED }
data class ToolCapability(val id:String,val name:String,val operations:Set<String>,val risk:ToolRisk,val availability:ToolAvailability,val limitation:String)
object ToolCapabilityCatalog {
 val tools=listOf(
  ToolCapability("gmail","Gmail",setOf("search_messages","get_message","create_draft"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.PARTIAL,"Lectura y borradores según autenticación; envío no debe asumirse disponible."),
  ToolCapability("github","GitHub",setOf("list_repositories","list_issues","get_issue","list_commits","list_workflow_runs","list_files","get_file","create_branch","commit_changes","create_pull_request"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.PARTIAL,"Lectura, workspace de archivos seleccionados, generación JSON y escritura con aprobación exacta; falta UI integrada, reparación iterativa y runner universal."),
  ToolCapability("google-maps","Google Maps",setOf("search_place","navigate"),ToolRisk.EXTERNAL_SIDE_EFFECT,ToolAvailability.PARTIAL,"Abre intents del sistema; navegación requiere acción del usuario."),
  ToolCapability("pescaia","PescaIA Surfcasting",setOf("search_public"),ToolRisk.READ_ONLY,ToolAvailability.PARTIAL,"Consulta páginas públicas concretas; no accede a cuentas ni todos los datos dinámicos."),
  ToolCapability("calendar","Calendario Android",setOf("read_events","create_event"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.PARTIAL,"Lectura y alta revisada por el usuario; no modifica ni elimina eventos."),
  ToolCapability("local-files","Archivos locales",setOf("read","create_draft"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.REQUIRES_CONFIGURATION,"Falta flujo general de documentos con selector y permisos."),
  ToolCapability("code-sandbox","Entorno de programación aislado",setOf("inspect","edit","build","test"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.NOT_IMPLEMENTED,"No hay sandbox de ejecución integrado en Android."),
  ToolCapability("browser","Navegador de investigación",setOf("search","open","extract","cite"),ToolRisk.READ_ONLY,ToolAvailability.PARTIAL,"Recuperación web HTML acotada; no es navegador completo ni procesa bien contenido dinámico/PDF."),
  ToolCapability("media-generation","Generación multimedia",setOf("generate_image","generate_video"),ToolRisk.EXTERNAL_SIDE_EFFECT,ToolAvailability.REQUIRES_CONFIGURATION,"Adaptadores ComfyUI disponibles en código, pero no hay endpoint/workflow registrado por defecto; exige prueba de conexión, workflow válido y consentimiento antes de transmitir prompts o archivos."),
  ToolCapability("knowledge-learning","Captura y revisión de conocimiento",setOf("capture_source","quarantine_candidate","review_candidate","publish_candidate"),ToolRisk.REVERSIBLE_WRITE,ToolAvailability.PARTIAL,"Solo mediante orden explícita; cuarentena y revisión humana; publicación condicionada por regresión. No reentrena pesos ni aprende automáticamente de conversaciones."),
  ToolCapability("autonomous-self-modification","Auto-modificación autónoma",setOf("train_model_weights","modify_own_source","change_safety_policy","self_publish"),ToolRisk.DESTRUCTIVE,ToolAvailability.NOT_IMPLEMENTED,"No implementado. ORION no se reprograma, reentrena ni cambia sus políticas por iniciativa propia.")
 )
 fun find(id:String):ToolCapability?=tools.firstOrNull{it.id==id}
}
