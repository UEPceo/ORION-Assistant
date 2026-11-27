package com.orion.voicelab.knowledge
enum class KnowledgePackState { CATALOG_ONLY, READY_TO_INSTALL, INSTALLED, UPDATE_AVAILABLE, ERROR }
data class KnowledgePackManifest(val id:String,val title:String,val version:Int,val domains:Set<KnowledgeDomain>,val jurisdiction:String?,val sourceIds:Set<String>,val licenseSummary:String,val estimatedSizeMb:Int?,val state:KnowledgePackState,val updateIntervalDays:Int)
/** Catalog metadata only: no pack counts as installed until content is bundled and verified. */
object KnowledgePackCatalog {
 val packs=listOf(
  KnowledgePackManifest("es-eu-law","Normativa España y Unión Europea",1,setOf(KnowledgeDomain.LAW_AND_REGULATION),"ES/EU",setOf("boe","eur-lex"),"Textos oficiales; revisar reutilización.",4,KnowledgePackState.READY_TO_INSTALL,7),
  KnowledgePackManifest("es-safety","Seguridad y emergencias España",1,setOf(KnowledgeDomain.HEALTH_AND_SAFETY),"ES",setOf("sanidad","dgt","miteco"),"Material oficial; no sustituye servicios de emergencia.",3,KnowledgePackState.READY_TO_INSTALL,30),
  KnowledgePackManifest("es-cyber","Ciberseguridad defensiva",1,setOf(KnowledgeDomain.CYBERSECURITY),"ES",setOf("incibe","aepd","owasp"),"Documentación pública; comprobar licencias.",3,KnowledgePackState.READY_TO_INSTALL,30),
  KnowledgePackManifest("android-kotlin","Desarrollo Android y Kotlin",1,setOf(KnowledgeDomain.PROGRAMMING,KnowledgeDomain.COMPUTER_SCIENCE),null,setOf("android","kotlin","github-docs"),"Licencias por recurso.",2,KnowledgePackState.READY_TO_INSTALL,30),
  KnowledgePackManifest("python-web","Python e Internet",1,setOf(KnowledgeDomain.PROGRAMMING,KnowledgeDomain.INTERNET),null,setOf("python","mdn"),"Documentación oficial; respetar licencias y versiones.",2,KnowledgePackState.READY_TO_INSTALL,60),
  KnowledgePackManifest("andalucia-fishing","Pesca recreativa Andalucía",2,setOf(KnowledgeDomain.FISHING,KnowledgeDomain.SURFCASTING,KnowledgeDomain.LAW_AND_REGULATION),"ES-AN",setOf("junta-andalucia","mapa","boe"),"Resúmenes de fuentes oficiales; revisar vigencia y cambios judiciales.",3,KnowledgePackState.READY_TO_INSTALL,7)
 )
 fun withRuntimeState(installedIds: Set<String>): List<KnowledgePackManifest> = packs.map { pack ->
  pack.copy(state = if (pack.id in installedIds) KnowledgePackState.INSTALLED else KnowledgePackState.READY_TO_INSTALL)
 }
}
