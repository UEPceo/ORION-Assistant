package com.orion.voicelab.creative

/**
 * Default free/open-weight engines. These are execution profiles, not claims that
 * the Android handset can run the models. The ComfyUI endpoint must be configured
 * by the user on hardware they control.
 */
data class CreativeEngineProfile(
 val id:String,
 val displayName:String,
 val task:String,
 val modelId:String,
 val runtime:String,
 val providerKind:MediaProviderKind,
 val license:String,
 val minimumDeploymentNote:String,
 val default:Boolean=false
)

object CreativeEngineCatalog {
 val IMAGE_PRIMARY = CreativeEngineProfile(
  id="comfyui-flux-schnell",
  displayName="FLUX.1-schnell via ComfyUI",
  task="image-generation",
  modelId="black-forest-labs/FLUX.1-schnell",
  runtime="ComfyUI",
  providerKind=MediaProviderKind.REMOTE,
  license="Apache-2.0",
  minimumDeploymentNote="Servidor propio con GPU; no se ejecuta en el móvil. Coste de software/modelo cero; hardware y electricidad no incluidos.",
  default=true
 )
 val IMAGE_FALLBACK = CreativeEngineProfile(
  id="comfyui-sd15",
  displayName="Stable Diffusion 1.5 via ComfyUI",
  task="image-generation",
  modelId="stable-diffusion-v1-5/stable-diffusion-v1-5",
  runtime="ComfyUI",
  providerKind=MediaProviderKind.REMOTE,
  license="CreativeML Open RAIL-M",
  minimumDeploymentNote="Alternativa ligera y madura; revisar licencia y condiciones del checkpoint concreto antes de uso comercial."
 )
 val VIDEO_PRIMARY = CreativeEngineProfile(
  id="comfyui-wan21-1.3b",
  displayName="Wan2.1 T2V-1.3B y perfil I2V-14B via ComfyUI",
  task="video-generation",
  modelId="Wan-AI/Wan2.1-T2V-1.3B",
  runtime="ComfyUI",
  providerKind=MediaProviderKind.REMOTE,
  license="Apache-2.0",
  minimumDeploymentNote="Modelo abierto; referencia oficial: 8.19 GB VRAM para T2V-1.3B y generación 480p. Para trabajo práctico se recomienda GPU dedicada y memoria adicional.",
  default=true
 )
 val VIDEO_IMAGE_TO_VIDEO = CreativeEngineProfile(
  id="comfyui-wan21-i2v-14b",
  displayName="Wan2.1 I2V-14B via ComfyUI",
  task="image-to-video",
  modelId="Wan-AI/Wan2.1-I2V-14B-480P",
  runtime="ComfyUI",
  providerKind=MediaProviderKind.REMOTE,
  license="Apache-2.0",
  minimumDeploymentNote="Checkpoint oficial I2V-14B-480P; requiere GPU de gama alta y mucha VRAM."
 )
 val VIDEO_QUALITY = CreativeEngineProfile(
  id="comfyui-wan21-14b",
  displayName="Wan2.1 14B via ComfyUI",
  task="video-generation",
  modelId="Wan-Video/Wan2.1",
  runtime="ComfyUI",
  providerKind=MediaProviderKind.REMOTE,
  license="Apache-2.0",
  minimumDeploymentNote="Perfil de mayor calidad y más exigente; requiere GPU de gama alta y mucha VRAM."
 )
 val defaults=listOf(IMAGE_PRIMARY,IMAGE_FALLBACK,VIDEO_PRIMARY,VIDEO_IMAGE_TO_VIDEO,VIDEO_QUALITY)
}
