# Native/JNI bridge classes must retain their names and members.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class org.nehuatl.llamacpp.** { *; }

# Preserve any Java/Kotlin methods invoked from native code.
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}

# Preserve runtime annotations and generic signatures used by libraries.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Parcelable creator metadata where Android or libraries inspect it.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ONNX Runtime Java/JNI API and custom text operators.
-keep class ai.onnxruntime.** { *; }
-keep class ai.onnxruntime.extensions.** { *; }
