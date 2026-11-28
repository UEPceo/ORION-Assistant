package com.orion.voicelab.brain
import java.util.Locale
object PlanningPromptComposer {
 fun isMultiStepRequest(text:String):Boolean {
  val v=text.lowercase(Locale.ROOT)
  val terms=listOf("planifica","haz un plan","crea un plan","prepara un plan","plan de trabajo","desglosa","organiza los pasos","paso a paso","de principio a fin","hasta terminar","resuelve de principio a fin","automatiza","encárgate de","encargate de","prepara y","investiga y","compara y","implementa y prueba")
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
- Las salidas pueden encadenarse como ${stepId.output} o ${stepId.summary}; declara siempre ese paso en dependsOn. Solo se resuelven salidas de pasos SUCCEEDED.
- Para tareas, notas y recordatorios locales, usa LOCAL_CREATE_TASK, LOCAL_CREATE_NOTE o LOCAL_CREATE_REMINDER; estos pasos no necesitan red, pero sí confirmación contextual antes de guardar.
- LOCAL_CREATE_TASK: connectorArguments {title, description?, notes?, category?, priority?}. LOCAL_CREATE_NOTE: {title?, content, category?}. LOCAL_CREATE_REMINDER: {title, reminderAtMs (epoch milliseconds futuro), description?, notes?, category?, priority?}.
- Ejecuta primero los pasos de lectura necesarios; el plan se pausará antes de una escritura no autorizada para mostrar sus entradas ya resueltas y pedir confirmación contextual. No autorices ni ejecutes el paso de escritura hasta que el usuario revise esa vista.
- Las escrituras locales y externas deben tener risk MEDIUM o superior y requiresApproval=true. No incluyas secretos ni credenciales.
- Pide confirmación específica antes de enviar, publicar, llamar, comprar, borrar o modificar servicios externos.
- Si falla un paso, bloquea sus dependientes y explica cómo reanudar.
Herramientas registradas y límites reales: $available
Solicitud: ${userText.take(1200)}"""
 }
}
