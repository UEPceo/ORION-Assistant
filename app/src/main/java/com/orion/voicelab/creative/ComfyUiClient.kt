package com.orion.voicelab.creative

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** User-configured ComfyUI endpoint. External HTTP is rejected; LAN/local HTTP is allowed. */
data class ComfyUiEndpointConfig(val baseUrl:String, val bearerToken:String?=null) {
    fun normalizedBaseUrl():String {
        val uri=java.net.URI(baseUrl.trim())
        require(uri.scheme=="https" || (uri.scheme=="http" && isLocalOrPrivateHost(uri.host.orEmpty()))) {
            "ComfyUI requiere HTTPS salvo un endpoint local/privado."
        }
        require(uri.userInfo.isNullOrBlank() && uri.host!=null && uri.query==null && uri.fragment==null) {
            "Endpoint no válido: no incluyas credenciales, query ni fragmento en la URL."
        }
        require(uri.port==-1 || uri.port in 1..65535)
        return uri.toASCIIString().trimEnd('/')
    }
    private fun isLocalOrPrivateHost(host:String):Boolean {
        val h=host.lowercase().trimEnd('.').removeSurrounding("[","]")
        if(h=="localhost" || h.endsWith(".localhost") || h.endsWith(".local")) return true
        val parts=h.split(".")
        if(parts.size!=4 || parts.any { (it.toIntOrNull() ?: -1) !in 0..255 }) return h=="::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80:")
        val a=parts[0].toInt(); val b=parts[1].toInt()
        return a==10 || a==127 || a==192&&b==168 || a==172&&b in 16..31 || a==169&&b==254
    }
}

data class ComfyUiWorkflowTemplate(
    val workflowJson:String,
    val positivePromptNodeId:String,
    val positivePromptInput:String="text",
    val negativePromptNodeId:String?=null,
    val negativePromptInput:String="text",
    val referenceImageNodeIds:List<String> = emptyList(),
    val dimensionNodeId:String?=null,
    val widthInput:String="width",
    val heightInput:String="height",
    val durationNodeId:String?=null,
    val durationInput:String="length",
    val framesPerSecond:Int=16
) {
    fun build(request:MediaGenerationRequest, uploadedReferences:List<String> = emptyList()):JSONObject {
        require(workflowJson.toByteArray(Charsets.UTF_8).size<=2*1024*1024) { "Workflow demasiado grande." }
        val root=JSONObject(workflowJson)
        require(root.length()>0) { "Workflow vacío." }
        val positive=root.optJSONObject(positivePromptNodeId)
            ?:throw IllegalArgumentException("No existe el nodo de prompt positivo configurado.")
        val inputs=positive.optJSONObject("inputs")?:throw IllegalArgumentException("El nodo positivo no tiene inputs.")
        require(inputs.has(positivePromptInput)) { "El input de prompt positivo no existe en el nodo." }
        inputs.put(positivePromptInput,request.prompt.take(12000))
        if(!request.negativePrompt.isNullOrBlank() && negativePromptNodeId!=null) {
            val node=root.optJSONObject(negativePromptNodeId)?:throw IllegalArgumentException("Nodo negativo inexistente.")
            val negative=node.optJSONObject("inputs")?:throw IllegalArgumentException("El nodo negativo no tiene inputs.")
            require(negative.has(negativePromptInput)) { "Input negativo no existe." }
            negative.put(negativePromptInput,request.negativePrompt.take(4000))
        }
        request.aspectRatio?.takeIf { dimensionNodeId!=null }?.let { ratio ->
            val parts=ratio.split(":")
            require(parts.size==2&&parts.all { (it.toIntOrNull() ?: 0) in 1..100 }) { "Formato de aspecto inválido. Usa por ejemplo 16:9." }
            val node=root.optJSONObject(dimensionNodeId)?:throw IllegalArgumentException("Nodo de dimensiones inexistente.")
            val nodeInputs=node.optJSONObject("inputs")?:throw IllegalArgumentException("Nodo de dimensiones sin inputs.")
            require(nodeInputs.has(widthInput)&&nodeInputs.has(heightInput)) { "Inputs de anchura/altura no configurados en el workflow." }
            val ratio=parts[0].toDouble()/parts[1].toDouble()
            val width=if(ratio>=1.0)768 else (768*ratio).toInt().coerceAtLeast(256)
            val height=if(ratio>=1.0)(768/ratio).toInt().coerceAtLeast(256) else 768
            val alignedWidth=(width/8)*8
            val alignedHeight=(height/8)*8
            nodeInputs.put(widthInput,alignedWidth);nodeInputs.put(heightInput,alignedHeight)
        }
        request.durationSeconds?.takeIf { durationNodeId!=null }?.let { seconds ->
            require(seconds in 1..60&&framesPerSecond in 1..60) { "Duración/FPS fuera de límites." }
            val node=root.optJSONObject(durationNodeId)?:throw IllegalArgumentException("Nodo de duración inexistente.")
            val nodeInputs=node.optJSONObject("inputs")?:throw IllegalArgumentException("Nodo de duración sin inputs.")
            require(nodeInputs.has(durationInput)) { "Input de frames/duración no configurado." }
            nodeInputs.put(durationInput,seconds*framesPerSecond+1)
        }
        require(uploadedReferences.size<=referenceImageNodeIds.size) { "No hay nodos configurados para todas las referencias." }
        uploadedReferences.forEachIndexed { index,filename ->
            val node=root.optJSONObject(referenceImageNodeIds[index])?:throw IllegalArgumentException("Nodo de referencia inexistente.")
            val nodeInputs=node.optJSONObject("inputs")?:throw IllegalArgumentException("Nodo de referencia sin inputs.")
            require(nodeInputs.has("image")) { "El nodo de referencia no contiene input image." }
            nodeInputs.put("image",filename)
        }
        return root
    }
}

data class ComfyUiOutput(val localFile:File,val mimeType:String,val remoteFilename:String,val subfolder:String)

class ComfyUiClient(private val context:Context, private val config:ComfyUiEndpointConfig) {
    private val base=config.normalizedBaseUrl()
    private fun connection(path:String,method:String="GET"):HttpURLConnection {
        val conn=URL(base+path).openConnection() as HttpURLConnection
        conn.requestMethod=method
        conn.instanceFollowRedirects=false
        conn.connectTimeout=15000
        conn.readTimeout=30000
        conn.setRequestProperty("Accept","application/json")
        config.bearerToken?.takeIf(String::isNotBlank)?.let { conn.setRequestProperty("Authorization","Bearer $it") }
        return conn
    }
    suspend fun runWorkflow(workflow:JSONObject,timeoutMs:Long=300000):ComfyUiOutput=withContext(Dispatchers.IO) {
        val request=JSONObject().put("prompt",workflow).put("client_id",UUID.randomUUID().toString())
        val submit=connection("/prompt","POST")
        submit.doOutput=true
        submit.setRequestProperty("Content-Type","application/json; charset=utf-8")
        submit.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
        val response=readResponse(submit)
        val promptId=response.optString("prompt_id")
        if(promptId.isBlank()) throw IOException("ComfyUI no devolvió prompt_id: ${response.optString("error")}")
        val started=System.currentTimeMillis()
        var completedOutput:JSONObject?=null
        while(System.currentTimeMillis()-started<timeoutMs) {
            val poll=connection("/history/${encodePath(promptId)}")
            val payload=readResponse(poll)
            val item=payload.optJSONObject(promptId)
            if(item!=null) {
                val status=item.optJSONObject("status")
                if(status?.optString("status_str")=="error") throw IOException("ComfyUI informó error: ${status.optJSONArray("messages")}")
                val output=extractOutput(item.optJSONObject("outputs"))
                if(output!=null) { completedOutput=output; break }
            }
            delay(1000)
        }
        val selected=completedOutput?:throw IOException("La generación agotó el tiempo de espera (${timeoutMs/1000}s).")
        val filename=selected.getString("filename")
        val subfolder=selected.optString("subfolder","")
        val type=selected.optString("type","output")
        val query="filename=${encodeQuery(filename)}&subfolder=${encodeQuery(subfolder)}&type=${encodeQuery(type)}"
        val download=connection("/view?$query")
        val declared=download.getHeaderFieldLong("Content-Length",-1L)
        require(declared<=MAX_OUTPUT_BYTES) { "La salida supera el límite de descarga." }
        val ext=filename.substringAfterLast('.', "bin").take(8)
        val file=File(context.cacheDir,"orion-media-${UUID.randomUUID()}.$ext")
        var total=0L
        download.inputStream.use { input -> file.outputStream().use { output ->
            val buffer=ByteArray(8192)
            while(true) {
                val count=input.read(buffer); if(count<0) break
                total+=count
                require(total<=MAX_OUTPUT_BYTES) { "La salida supera el límite de descarga." }
                output.write(buffer,0,count)
            }
        } }
        require(total>0L) { "Archivo multimedia vacío." }
        download.disconnect()
        ComfyUiOutput(file,mimeForExtension(ext),filename,subfolder)
    }
    suspend fun uploadReference(asset:MediaAsset):String=withContext(Dispatchers.IO) {
        val resolver=context.contentResolver
        val size=asset.sizeBytes?:resolver.openAssetFileDescriptor(asset.uri,"r")?.use { it.length }
        require(size!=null && size in 1..MAX_UPLOAD_BYTES) { "Referencia multimedia supera el límite de ${MAX_UPLOAD_BYTES/1024/1024} MB." }
        val filename=(asset.displayName?.takeIf(String::isNotBlank)?: "orion-reference").replace(Regex("[^A-Za-z0-9._-]"),"_")
        val boundary="ORION-${UUID.randomUUID()}"
        val conn=connection("/upload/image","POST")
        conn.doOutput=true
        conn.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
        conn.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"$filename\"\r\nContent-Type: ${asset.mimeType}\r\n\r\n".toByteArray())
            resolver.openInputStream(asset.uri)?.use { input ->
                val buffer=ByteArray(8192); var total=0L
                while(true) {
                    val count=input.read(buffer); if(count<0) break
                    total+=count
                    require(total<=MAX_UPLOAD_BYTES) { "Referencia supera el límite permitido." }
                    out.write(buffer,0,count)
                }
            }?:throw IOException("No se pudo leer archivo de referencia.")
            out.write("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\ninput\r\n--$boundary--\r\n".toByteArray())
        }
        val result=readResponse(conn)
        result.optString("name").takeIf(String::isNotBlank)?:throw IOException("ComfyUI no confirmó la subida de referencia.")
    }
    private fun readResponse(conn:HttpURLConnection):JSONObject {
        val code=conn.responseCode
        if(code in 300..399) { conn.disconnect(); throw IOException("ComfyUI redirect bloqueado por seguridad.") }
        val stream=if(code in 200..299) conn.inputStream else conn.errorStream
        val text=stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(2*1024*1024) }.orEmpty()
        conn.disconnect()
        if(code !in 200..299) throw IOException("ComfyUI HTTP $code: ${text.take(500)}")
        return JSONObject(text)
    }
    private fun extractOutput(outputs:JSONObject?):JSONObject? {
        if(outputs==null)return null
        outputs.keys().forEach { node ->
            val item=outputs.optJSONObject(node)?:return@forEach
            listOf("images","gifs","videos").forEach { key ->
                val list=item.optJSONArray(key)?:return@forEach
                if(list.length()>0) return list.optJSONObject(0)
            }
        }
        return null
    }
    private fun encodePath(value:String)=URLEncoder.encode(value,"UTF-8").replace("+","%20")
    private fun encodeQuery(value:String)=URLEncoder.encode(value,Charsets.UTF_8)
    private fun mimeForExtension(ext:String)=when(ext.lowercase()) { "png"->"image/png"; "jpg","jpeg"->"image/jpeg"; "webp"->"image/webp"; "mp4"->"video/mp4"; "webm"->"video/webm"; "gif"->"image/gif"; else->"application/octet-stream" }
    companion object { const val MAX_UPLOAD_BYTES=25L*1024*1024; const val MAX_OUTPUT_BYTES=150L*1024*1024 }
}
