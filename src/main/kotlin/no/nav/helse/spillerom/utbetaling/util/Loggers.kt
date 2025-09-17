package no.nav.helse.bakrommet.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline val <reified T> T.logg: Logger
    get() = LoggerFactory.getLogger(T::class.java)
val sikkerLogger: Logger = LoggerFactory.getLogger("teamlogs")
