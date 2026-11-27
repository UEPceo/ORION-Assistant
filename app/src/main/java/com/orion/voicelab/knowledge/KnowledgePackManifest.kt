package com.orion.voicelab.knowledge

enum class KnowledgePackState { CATALOG_ONLY, READY_TO_INSTALL, INSTALLED, UPDATE_AVAILABLE, ERROR }
data class KnowledgePackManifest(val id:String,val title:String,val version:Int,val domains:Set<KnowledgeDomain>,val jurisdiction:String?,val sourceIds:Set<String>,val licenseSummary:String,val estimatedSizeMb:Int?,val state:KnowledgePackState,val updateIntervalDays:Int)

/** Static metadata for source-backed packs; runtime installation state is derived locally. */
object KnowledgePackCatalog {
 val packs=listOf(
  KnowledgePackManifest("es-eu-law","Normativa España y Unión Europea",1,setOf(KnowledgeDomain.LAW_AND_REGULATION),"ES/EU",setOf("boe","eur-lex"),"Textos oficiales; revisar reutilización.",1,KnowledgePackState.READY_TO_INSTALL,7),
  KnowledgePackManifest("es-safety","Seguridad y emergencias España y UE",2,setOf(KnowledgeDomain.HEALTH_AND_SAFETY),"ES",setOf("eu-112"),"Material oficial; no sustituye servicios de emergencia.",1,KnowledgePackState.READY_TO_INSTALL,30),
  KnowledgePackManifest("es-cyber","Ciberseguridad defensiva",2,setOf(KnowledgeDomain.CYBERSECURITY),"ES",setOf("incibe","owasp"),"Documentación pública; comprobar licencias.",3,KnowledgePackState.READY_TO_INSTALL,30),
  KnowledgePackManifest("android-kotlin","Desarrollo Android y Kotlin",2,setOf(KnowledgeDomain.PROGRAMMING),null,setOf("android","kotlin"),"Licencias por recurso.",1,KnowledgePackState.READY_TO_INSTALL,30),
  KnowledgePackManifest("python-web","Python e Internet",3,setOf(KnowledgeDomain.PROGRAMMING,KnowledgeDomain.INTERNET),null,setOf("python","mdn"),"Documentación oficial; respetar licencias y versiones.",2,KnowledgePackState.READY_TO_INSTALL,60),
  KnowledgePackManifest("andalucia-fishing","Pesca recreativa Andalucía",2,setOf(KnowledgeDomain.FISHING,KnowledgeDomain.SURFCASTING,KnowledgeDomain.LAW_AND_REGULATION),"ES-AN",setOf("junta-andalucia"),"Resúmenes de fuentes oficiales; revisar vigencia y cambios judiciales.",3,KnowledgePackState.READY_TO_INSTALL,7),
  KnowledgePackManifest("computer-science","Informática y computación",1,setOf(KnowledgeDomain.COMPUTER_SCIENCE),null,setOf("nist","mit-ocw"),"Síntesis educativa; respetar licencias.",1,KnowledgePackState.READY_TO_INSTALL,90),
  KnowledgePackManifest("mathematics","Matemáticas",1,setOf(KnowledgeDomain.MATHEMATICS),null,setOf("openstax","mit-ocw"),"Textos educativos revisados; comprobar edición.",1,KnowledgePackState.READY_TO_INSTALL,180),
  KnowledgePackManifest("science","Ciencia",1,setOf(KnowledgeDomain.SCIENCE),null,setOf("nasa-science","noaa"),"Material educativo; verificar estudios concretos.",1,KnowledgePackState.READY_TO_INSTALL,90),
  KnowledgePackManifest("engineering","Ingeniería",1,setOf(KnowledgeDomain.ENGINEERING),null,setOf("nasa-systems","nist-si"),"Síntesis técnica; no sustituye normas ni certificaciones.",1,KnowledgePackState.READY_TO_INSTALL,180),
  KnowledgePackManifest("finance","Finanzas",1,setOf(KnowledgeDomain.FINANCE),"ES",setOf("cnmv","banco-espana"),"Fuentes oficiales; no es asesoramiento financiero.",1,KnowledgePackState.READY_TO_INSTALL,30),
  KnowledgePackManifest("business","Empresa y emprendimiento",1,setOf(KnowledgeDomain.BUSINESS),"EU/OECD",setOf("oecd-sme","eu-sme"),"Fuentes institucionales; verificar obligaciones vigentes.",1,KnowledgePackState.READY_TO_INSTALL,90),
  KnowledgePackManifest("communication","Comunicación y escritura",1,setOf(KnowledgeDomain.COMMUNICATION),null,setOf("w3c-wai","purdue-owl"),"Guías educativas y técnicas; respetar derechos.",1,KnowledgePackState.READY_TO_INSTALL,180),
  KnowledgePackManifest("creativity","Creatividad y diseño",1,setOf(KnowledgeDomain.CREATIVE_DESIGN,KnowledgeDomain.IMAGE_VIDEO),null,setOf("creative-commons","adobe"),"Verificar licencias de cada obra.",1,KnowledgePackState.READY_TO_INSTALL,90),
  KnowledgePackManifest("general-knowledge","Conocimiento general",1,setOf(KnowledgeDomain.GENERAL),null,setOf("unesco-il","nasa-earth"),"Síntesis educativa; separar hechos e inferencias.",1,KnowledgePackState.READY_TO_INSTALL,90)
 )
 fun withRuntimeState(installedIds:Set<String>):List<KnowledgePackManifest> = packs.map { p ->
  p.copy(state=if(p.id in installedIds) KnowledgePackState.INSTALLED else KnowledgePackState.READY_TO_INSTALL)
 }
}
