package org.archguard.diff.changes

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import infrastructure.DBIStore
import infrastructure.task.SqlExecuteThreadPool
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Phaser
import kotlin.io.path.Path
import kotlin.io.path.exists

class Runner : CliktCommand() {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val store = DBIStore.getInstance()

    private val branch: String by option(help = "git repository branch").default("master")
    private val since: String by option(help = "since specific revision").default("0")
    private val until: String by option(help = "util specific revision").default("0")

    private val path: String by option(help = "local path").default(".")

    private val systemId: String by option(help = "system id").default("0")
    private val language: String by option(help = "language").default("")


    // todo: add multiple languages support
    override fun run() {
        logger.info("diff from $since to $until on branch: $branch with path: $path")

        val differ = GitDiffer(path, branch)

        var sinceRev = since
        if(since.length > 7) {
            sinceRev = sinceRev.substring(0, 7)
        }
        var untilRev = until
        if(until.length > 7) {
            untilRev = untilRev.substring(0, 7)
        }

        val changedCalls = differ.countBetween(sinceRev, untilRev)

        logger.info("found changes: ${changedCalls.size}")
        logger.info("start insert data into Mysql")
        val sqlStart = System.currentTimeMillis()

        val tableName = "scm_diff_change"
        val repository = DiffRepository(systemId, language, since, until, tableName)

        // logs for debug
        File("changes.json").writeText(Json.encodeToString(changedCalls))

        repository.saveDiff(changedCalls)
        repository.close()

        storeDatabase(systemId, tableName)

        val sqlEnd = System.currentTimeMillis()
        logger.info("Insert into MySql spend: {} s", (sqlEnd - sqlStart) / 1000)
        SqlExecuteThreadPool.close()
    }

    private fun storeDatabase(systemId: String, tableName: String) {
        store.disableForeignCheck()
        store.initConnectionPool()

        logger.info("--------------------------------------------------------")
        val phaser = Phaser(1)
        deleteByTable(tableName, phaser, systemId)
        phaser.arriveAndAwaitAdvance()

        logger.info("------------ system {} clean db is done --------------", systemId)
        saveByTables(arrayOf(tableName), phaser)
        phaser.arriveAndAwaitAdvance()

        logger.info("------------ system {} update db is done --------------", systemId)
        logger.info("--------------------------------------------------------")
        store.enableForeignCheck()
    }

    private fun saveByTables(tables: Array<String>, phaser: Phaser) {
        for (table in tables) {
            val sStart = System.currentTimeMillis()
            val sqlPath = Path("$table.sql")
            if (!sqlPath.exists()) {
                continue
            }

            val sqls: List<String> = Files.readAllLines(sqlPath)
            if (sqls.isNotEmpty()) {
                store.save(sqls, table, phaser)
                val sEnd = System.currentTimeMillis()
                logger.info(
                    "save table {} with records {} spend {}",
                    table,
                    sqls.size,
                    (sEnd - sStart) / 1000
                )
            }
        }
    }

    private fun deleteByTable(table: String, phaser: Phaser, systemId: String) {
        val dStart = System.currentTimeMillis()
        store.delete(table, phaser, systemId)
        val dEnd = System.currentTimeMillis()
        logger.info("delete {} spend {}", table, (dEnd - dStart) / 1000)
    }
}

fun main(args: Array<String>) = Runner().main(args)

