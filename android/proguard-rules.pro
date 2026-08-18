# libGDX
-verbose
-dontwarn android.support.**
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.jnigen.BuildTarget*

-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* {
   <init>(com.badlogic.gdx.Application, android.content.Context, java.lang.Object, com.badlogic.gdx.backends.android.AndroidApplicationConfiguration);
}

# POJOs serializados/deserializados por reflexión con com.badlogic.gdx.utils.Json
-keep class com.pcdc.td.data.** { *; }
-keep class com.pcdc.td.meta.PlayerProfile { *; }
