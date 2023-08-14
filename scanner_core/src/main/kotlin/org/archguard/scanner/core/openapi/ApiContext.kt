package org.archguard.scanner.core.openapi

import org.archguard.scanner.core.context.AnalyserType
import org.archguard.scanner.core.context.Context

interface ApiContext: Context {
    override val type: AnalyserType get() = AnalyserType.OPEN_API
    val path: String
    val branch: String
}

