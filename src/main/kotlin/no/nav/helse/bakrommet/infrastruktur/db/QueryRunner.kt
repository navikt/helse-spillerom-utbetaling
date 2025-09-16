package no.nav.helse.bakrommet.infrastruktur.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.action.QueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import javax.sql.DataSource

sealed class QueryRunner protected constructor() {
    fun <T> single(
        sql: String,
        vararg params: Pair<String, Any>,
        mapper: (Row) -> T?,
    ): T? = run(queryOf(sql, params.toMap()).map(mapper).asSingle)

    fun update(
        sql: String,
        vararg params: Pair<String, Any?>,
    ): Int = run(queryOf(sql, params.toMap()).asUpdate)

    fun execute(sql: String): Boolean = run(queryOf(sql).asExecute)

    fun <T> list(
        sql: String,
        vararg params: Pair<String, Any>,
        mapper: (Row) -> T,
    ): List<T> =
        run(
            queryOf(sql, params.toMap())
                .map(mapper)
                .asList,
        )

    protected abstract fun <A> run(action: QueryAction<A>): A
}

class MedSession(private val session: Session) : QueryRunner() {
    override fun <A> run(action: QueryAction<A>): A = action.runWithSession(session)
}

class MedDataSource(private val dataSource: DataSource) : QueryRunner() {
    override fun <A> run(action: QueryAction<A>): A =
        sessionOf(dataSource = dataSource, strict = true).use { session ->
            action.runWithSession(session)
        }
}
