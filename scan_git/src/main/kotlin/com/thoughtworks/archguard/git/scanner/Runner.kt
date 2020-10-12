package com.thoughtworks.archguard.git.scanner

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.thoughtworks.archguard.git.scanner.complexity.CognitiveComplexityParser
import com.thoughtworks.archguard.git.scanner.helper.Bean2Sql
import com.thoughtworks.archguard.git.scanner.loc.JavaLoCApp
import com.github.ajalt.clikt.parameters.options.default as default1

class Runner : CliktCommand() {
    private val branch: String by option(help = "git repository branch").default1("master")
    private val after: String by option(help = "scanner only scan commits after this timestamp").default1("0")

    private val path: String by option(help = "local path").default1(".")
    private val repoId: String by option(help = "repo id").default1("0")
    private val systemId: String by option(help = "system id").default1("0")
    private val loc: String? by option(help = "scan loc")

    override fun run() {
        if (loc == null) {
            val service = ScannerService(JGitAdapter(CognitiveComplexityParser()), Bean2Sql())
            service.git2SqlFile(path, branch, after, repoId, systemId.toLong())
        } else {
            val javaLoCApp = JavaLoCApp(systemId)
            javaLoCApp.analysisDir(path)
        }

    }
}

fun main(args: Array<String>) = Runner().main(args)


