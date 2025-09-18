package no.nav.helse.spillerom.utbetaling.kafka

interface KafkaConsumerInterface {
    fun start()

    fun stop()
}
