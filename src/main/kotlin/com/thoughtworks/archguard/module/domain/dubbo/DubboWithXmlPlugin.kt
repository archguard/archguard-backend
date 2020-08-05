package com.thoughtworks.archguard.module.domain.dubbo

import com.thoughtworks.archguard.module.domain.model.JClassVO
import com.thoughtworks.archguard.module.domain.plugin.PluginType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DubboWithXmlPlugin : DubboPlugin() {

    @Autowired
    lateinit var xmlConfigService: XmlConfigService

    override fun getPluginType(): PluginType {
        return PluginType.DUBBO_WITH_XML
    }

    override fun mapCalleeToReal(caller: JClassVO, callee: JClassVO): List<JClassVO>{
        val implements = jClassRepository.findClassImplements(callee.name, callee.module).map { it.toVO() }
        val calleeSubModuleByXml = xmlConfigService.getRealCalleeModuleByXmlConfig(caller, callee)
        val realCallee = implements.filter { calleeSubModuleByXml.any{subModuleDubbo ->  subModuleDubbo.name == it.module} }
        if (realCallee.isEmpty()) {
            return implements
        }
        return realCallee
    }

}