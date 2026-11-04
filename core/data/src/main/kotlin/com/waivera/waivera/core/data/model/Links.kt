package com.waivera.waivera.core.data.model

/** A published resource as a chat link preview shows it. */
data class LinkedResource(
    val id: String,
    val title: String,
    /** Left out when it only repeats the title. */
    val provider: String?,
    val city: String?,
)

/** A published FAQ article the reader may open. */
data class LinkedFaq(val id: String, val slug: String, val title: String, val summary: String?)
