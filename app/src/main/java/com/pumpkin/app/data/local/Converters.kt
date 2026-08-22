package com.pumpkin.app.data.local

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        // JSONObject.put(key, List) does NOT convert the list to a JSON
        // array — it has no special handling for a raw Kotlin/Java List, so
        // it silently falls back to storing the list's own toString() as a
        // single JSON string (e.g. "[a, b]", quotes and all). toStringList's
        // optJSONArray then never finds a real array and always returns
        // emptyList(), no matter what was written. Building the JSONArray
        // explicitly is what makes this round-trip actually work.
        val array = JSONArray()
        value.forEach { array.put(it) }
        return JSONObject().apply { put("items", array) }.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val array = JSONObject(value).optJSONArray("items") ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    @TypeConverter
    fun fromStringLongMap(value: Map<String, Long>): String {
        val json = JSONObject()
        value.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    @TypeConverter
    fun toStringLongMap(value: String): Map<String, Long> {
        val json = JSONObject(value)
        return json.keys().asSequence().associateWith { json.getLong(it) }
    }

    @TypeConverter
    fun fromStringStringMap(value: Map<String, String>): String {
        val json = JSONObject()
        value.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    @TypeConverter
    fun toStringStringMap(value: String): Map<String, String> {
        val json = JSONObject(value)
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
}
