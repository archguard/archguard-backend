package com.thoughtworks.archguard.scanner.sourcecode.frontend.identify

import com.thoughtworks.archguard.scanner.sourcecode.frontend.ContainerDemand
import chapi.domain.core.CodeCall
import chapi.domain.core.CodeImport

interface HttpIdentify {
    fun isMatch(call: CodeCall, imports: Array<CodeImport>): Boolean
    fun convert(call: CodeCall): ContainerDemand
}
