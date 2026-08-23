# Firestore deserializes into these data classes via reflection — keep them.
-keep class com.pumpkin.app.data.model.** { *; }

# Gson (de)serializes these into REST request/response bodies via reflection —
# without this, R8 renames their fields in release builds and the server
# receives JSON keyed by mangled names instead of e.g. "partnerEmail".
-keep class com.pumpkin.app.data.remote.api.dto.** { *; }

# Standard Gson + R8 rules: preserve generic signatures and annotations
# Gson's reflection-based (de)serialization relies on.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
