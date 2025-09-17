package no.nav.helse.spillerom.utbetaling.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline val <reified T> T.logg: Logger
    get() = LoggerFactory.getLogger(T::class.java)
val sikkerLogger: Logger = LoggerFactory.getLogger("teamlogs")
