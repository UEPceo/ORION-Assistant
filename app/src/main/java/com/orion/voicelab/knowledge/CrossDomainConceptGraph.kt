package com.orion.voicelab.knowledge

/** Curated one-hop associations improve discovery, not evidence or truth. */
object CrossDomainConceptGraph {
    private val aliases = mapOf(
        "authentication" to setOf("login","signin","credential","password","contrasena","autenticacion","identidad","clave","acceso","credencial","pin"),
        "security" to setOf("seguridad","proteccion","defensa","vulnerabilidad","amenaza","ataque","ciberseguridad"),
        "emergency" to setOf("emergencia","urgencia","auxilio","112","socorro","accidente"),
        "law" to setOf("ley","normativa","legislacion","reglamento","decreto","legal","juridico"),
        "fishing" to setOf("pesca","pescador","captura","anzuelo","cebo","surfcasting","licencia","mareas","marea","playa"),
        "programming" to setOf("programacion","codigo","desarrollo","software","algoritmo","kotlin","python"),
        "web" to setOf("internet","http","https","servidor","navegador","api","web"),
        "concurrency" to setOf("coroutine","coroutines","concurrencia","paralelo","asincrono","suspend"),
        "finance" to setOf("finanzas","dinero","inversion","presupuesto","coste","ingresos","gastos","ahorro","credito","hipoteca","tae","acciones","bonos","interes"),
        "mathematics" to setOf("matematicas","algebra","calculo","derivada","integral","ecuacion","geometria","estadistica","probabilidad","teorema"),
        "science" to setOf("ciencia","cientifico","fisica","quimica","biologia","astronomia","experimento","hipotesis","investigacion"),
        "engineering" to setOf("ingenieria","mecanica","electronica","termodinamica","metrologia","materiales","sistemas"),
        "business" to setOf("empresa","negocio","emprendimiento","pyme","ventas","marketing","cliente","mercado","productividad"),
        "communication" to setOf("comunicacion","redaccion","escritura","oratoria","argumentacion","presentacion","narrativa","accesibilidad"),
        "creativity" to setOf("creatividad","diseno","tipografia","composicion","prototipo","licencia","autor"),
        "weather" to setOf("meteorologia","tiempo","clima","viento","wind","oleaje","lluvia","temperatura","presion"),
        "geography" to setOf("geografia","cartografia","mapa","ubicacion","coordenadas","territorio","localidad"),
        "data" to setOf("datos","dataset","base","estadistica","analitica","medicion","metricas"),
        "energy" to setOf("energia","electricidad","potencia","bateria","consumo","voltaje","corriente"),
        "health" to setOf("salud","medicina","medico","sintoma","diagnostico","biomedicina")
    )
    private val links = listOf(
        "fishing" to "weather", "fishing" to "geography", "fishing" to "engineering", "fishing" to "science",
        "weather" to "science", "weather" to "geography", "geography" to "data",
        "programming" to "security", "programming" to "mathematics", "programming" to "engineering", "programming" to "data",
        "web" to "security", "web" to "communication", "authentication" to "security", "security" to "law", "security" to "engineering",
        "mathematics" to "science", "mathematics" to "data", "science" to "engineering", "engineering" to "energy", "engineering" to "health",
        "data" to "finance", "finance" to "business", "business" to "communication", "business" to "creativity",
        "communication" to "creativity", "creativity" to "engineering", "health" to "science",
        "law" to "business", "law" to "communication", "emergency" to "health", "emergency" to "communication", "emergency" to "geography"
    )
    fun directConcepts(token:String):Set<String> = aliases.filter { (concept, words) -> token == concept || token in words }.keys
    fun associatedConcepts(seedConcepts:Set<String>):Map<String,Double> {
        val result=linkedMapOf<String,Double>()
        links.forEach { (a,b) ->
            if(a in seedConcepts && b !in seedConcepts) result[b]=maxOf(result[b]?:0.0,ASSOCIATION_WEIGHT)
            if(b in seedConcepts && a !in seedConcepts) result[a]=maxOf(result[a]?:0.0,ASSOCIATION_WEIGHT)
        }
        return result
    }
    fun conceptsForText(text:String):Set<String> {
        val normalized=java.text.Normalizer.normalize(text.lowercase(),java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return normalized.split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank).flatMap(::directConcepts).toSet()
    }
    const val ASSOCIATION_WEIGHT=0.24
}
