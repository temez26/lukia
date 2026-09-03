package io.lukia.core.infrastructure.diagnostics

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.failure
import io.lukia.core.domain.diagnostics.info

/**
 * The app's schema, with what it does to somebody's library written down.
 *
 * Wrapping the schema rather than registering migration callbacks with the
 * drivers: each of the three takes those through a parameter of its own, so the
 * callback route is the same small change written three different ways and got
 * wrong three different ways. A schema is one interface on every platform.
 *
 * What this can and cannot see is worth being plain about. [create] and
 * [migrate] are called only on the run that actually creates or migrates, so a
 * reader who turns diagnostics on *after* a bad update finds nothing here about
 * it - the migration already happened, and it does not happen twice. What this
 * covers is the reader who already had diagnostics on when the update landed,
 * and - once a crash leaves a file behind - the migration that took the app down
 * with it.
 */
fun SqlSchema<QueryResult.Value<Unit>>.recordingTo(
    diagnostics: Diagnostics,
): SqlSchema<QueryResult.Value<Unit>> = RecordingSchema(this, diagnostics)

private class RecordingSchema(
    private val schema: SqlSchema<QueryResult.Value<Unit>>,
    private val diagnostics: Diagnostics,
) : SqlSchema<QueryResult.Value<Unit>> {

    override val version: Long get() = schema.version

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        // Once in the life of an installation, and worth having: it dates the
        // library, which is the difference between "this has always been wrong"
        // and "this started after the update".
        diagnostics.info(
            area = DiagnosticArea.Storage,
            event = "schema-created",
            detail = "version ${schema.version}",
        )

        return schema.create(driver)
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> {
        val versions = "$oldVersion -> $newVersion"

        // Recorded either way and rethrown unchanged. A migration that fails
        // takes the app down on its next query with nothing anywhere saying
        // which step it died on, and that is the one failure in this app a
        // reader cannot work around or describe.
        return runCatching { schema.migrate(driver, oldVersion, newVersion, *callbacks) }
            .onFailure {
                diagnostics.failure(
                    area = DiagnosticArea.Storage,
                    event = "migration-failed",
                    detail = versions,
                    cause = it,
                )
            }
            .onSuccess {
                diagnostics.info(
                    area = DiagnosticArea.Storage,
                    event = "schema-migrated",
                    detail = versions,
                )
            }
            .getOrThrow()
    }
}
