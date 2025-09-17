package no.nav.helse.bakrommet.infrastruktur.db

import kotliquery.Session
import kotliquery.sessionOf
import javax.sql.DataSource

class TransactionalSessionFactory<out SessionDaosType>(private val dataSource: DataSource, private val daosCreatorFunction: (Session) -> SessionDaosType) {
    fun <RET> transactionalSessionScope(transactionalBlock: (SessionDaosType) -> RET): RET =
        sessionOf(dataSource, returnGeneratedKey = true).use { session ->
            session.transaction { transactionalSession ->
                transactionalBlock(daosCreatorFunction(transactionalSession))
            }
        }
}

/**
 * Convenience-wrapper rundt et objekt med ikke-transaksjonelle DAOer, og en TransactionalSessionFactory basert på samme interface,
 * som bl.a. kan sikre at man kun forholder seg til nonTransactional eller Transactional- versjon
 * av et Daoer-interface av gangen (per kode-blokk), ved at riktig versjon av DAO-samlingen tilgjengeliggjøres som "this"
 * (og ved at "daoer" og "sessionFactory" da ikke trenger være tilgjengelig i Servicen som "val"-verdier).
 */
class DbDaoer<DaosType>(private val daoer: DaosType, private val sessionFactory: TransactionalSessionFactory<DaosType>) {
    fun <RET> nonTransactional(block: (DaosType.() -> RET)): RET {
        return block(daoer)
    }

    fun <RET> transactional(block: (DaosType.() -> RET)): RET {
        return sessionFactory.transactionalSessionScope { session ->
            block(session)
        }
    }
}
