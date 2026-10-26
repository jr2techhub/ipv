# IPV-POS: reglas de ProGuard/R8 mínimas (Stack y Compose traen sus propias reglas)
-keepclassmembers class com.ipv.pos.** { *; }
-dontwarn org.slf4j.**
