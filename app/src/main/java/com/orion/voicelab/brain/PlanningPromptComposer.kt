package com.orion.voicelab.brain
import java.util.Locale
object PlanningPromptComposer {
 fun isMultiStepRequest(text:String):Boolean {
  val v=text.lowercase(Locale.ROOT)
  val terms=listOf("planifica","organiza los pasos","paso a paso","de principio a fin","hasta terminar","automatiza","encárgate de","encargate de","prepara y","investiga y","compara y","implementa y prueba")
  return terms.any(v::contains)||Regex("\\b(primer[oa]|después|luego|finalmente)\\b").containsMatchIn(v)
 }
 fun compose(userText:String):String {
  if(!isMultiStepRequest(userText))return ""
  val available = ToolCapabilityCatalog.tools.filter { it.availability == ToolAvailability.OPERATIONAL || it.availability == ToolAvailability.PARTIAL }
   .joinToString("; ") { "${it.name}: ${it.availability} (${it.limitation})" }.take(700)
  return """MODO DE PLANIFICACIÓN MULTIPASO:
- Descompón el objetivo en pasos pequeños, ordenados y verificables.
- Indica dependencias, resultado esperado y riesgo de cada paso.
- Separa lectura/investigación de escrituras y acciones externas.
- Nunca declares ejecutado un paso sin éxito verificable de la herramienta.
- No inventes disponibilidad: si no existe ejecutor, entrega el plan y declara que no se ha ejecutado.
- Pide confirmación específica antes de enviar, publicar, llamar, comprar, borrar o modificar servicios externos.
- Si falla un paso, bloquea sus dependientes y explica cómo reanudar.
Herramientas registradas y límites reales: $available\nSolicitud: ${userText.take(1200)}"""
 }
}
