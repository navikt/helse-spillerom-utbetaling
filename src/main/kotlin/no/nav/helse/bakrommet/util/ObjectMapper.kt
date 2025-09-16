package no.nav.helse.bakrommet.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

val objectMapper: ObjectMapper =
    ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)

fun <T> T.tilJsonNode(): JsonNode = objectMapper.valueToTree(this)

fun String.asJsonNode(): JsonNode {
    return objectMapper.readTree(this)
}

fun String.asStringStringMap(): Map<String, String> {
    return objectMapper.readValue(this)
}

fun Any.toJsonNode(): JsonNode {
    return objectMapper.valueToTree(this)
}

inline fun <reified T> String.somListe(): List<T> {
    return objectMapper.readValue(
        this,
        objectMapper.typeFactory.constructCollectionType(
            List::class.java,
            T::class.java,
        ),
    )
}

inline fun <reified T> JsonNode.somListe(): List<T> {
    return objectMapper.convertValue(
        this,
        objectMapper.typeFactory.constructCollectionType(
            List::class.java,
            T::class.java,
        ),
    )
}

inline fun <reified T> JsonNode.deserialize(): T = objectMapper.convertValue(this, T::class.java)
