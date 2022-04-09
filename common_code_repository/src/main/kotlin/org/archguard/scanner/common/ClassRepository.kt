package org.archguard.scanner.common

import chapi.app.frontend.path.importConvert
import chapi.domain.core.*
import infrastructure.SourceBatch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.archguard.scanner.common.RepositoryHelper.currentTime
import org.archguard.scanner.common.RepositoryHelper.generateId
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private const val DEFAULT_MODULE_NAME = "root"
private const val THIRD_PARTY = "root"

class ClassRepository(systemId: String, language: String, workspace: String) {
    private val batch: SourceBatch = SourceBatch()
    private val count = AtomicInteger(0)
    private val batchStep = 100
    private val systemId: String
    private val language: String
    private val workspace: String

    init {
        this.systemId = systemId
        this.language = language
        this.workspace = workspace
    }

    fun saveClassItem(clz: CodeDataStruct) {
        val clzId = saveClass(clz)
        saveClassFields(clzId, clz.Fields, clz.NodeName)
        saveClassMethods(clzId, clz.Functions, clz.NodeName, clz.Package)

        count.incrementAndGet()
        if (count.get() == batchStep) {
            flush()
            count.compareAndSet(batchStep, 0)
        }
    }

    fun saveClassBody(clz: CodeDataStruct) {
        val clzId = saveOrGetClzId(clz)!!
        saveClassDependencies(clzId, clz.Imports, clz.Package, clz.NodeName, clz.FilePath, clz.Functions)
        saveClassCallees(clz.Functions, DEFAULT_MODULE_NAME, clz.NodeName, clz.Package)
        saveClassParent(clzId, DEFAULT_MODULE_NAME, clz.Imports, clz.Extend)
        saveClassAnnotation(clzId, clz.Annotations)
    }

    private fun saveOrGetClzId(clz: CodeDataStruct): String? {
        val idOrOpt = findClass("${clz.Package}.${clz.NodeName}", DEFAULT_MODULE_NAME)
        return idOrOpt.orElseGet {
            saveClass(clz)
        }
    }

    private fun saveClassAnnotation(clzId: String, annotations: Array<CodeAnnotation>) {
        annotations.forEach {
            doSaveAnnotation(it, clzId)
        }
    }

    private fun saveClassCallees(functions: Array<CodeFunction>, moduleName: String, clzName: String, pkgName: String) {
        for (function in functions) {
            val mId = findMethodIdByClzName(function, clzName, function.Name, pkgName)?.orElse("") ?: continue
            for (call in function.FunctionCalls) {
                saveMethodCall(mId, call, moduleName, clzName, call.Package)
            }
        }
    }

    private fun saveMethodCall(callerId: String, callee: CodeCall, moduleName: String, clzName: String, pkgName: String) {
        val calleeId: String? = saveOrGetCalleeMethod(callee, moduleName, clzName, pkgName)
        val callees: MutableMap<String, String> = HashMap()
        callees["id"] = generateId()
        callees["system_id"] = systemId
        callees["a"] = callerId
        callees["b"] = calleeId.orEmpty()
        batch.add("code_ref_method_callees", callees)
    }

    private fun saveOrGetCalleeMethod(callee: CodeCall, module: String, clzName: String, pkgName: String): String? {
        val methodId: Optional<String?>? = findCalleeMethodId(module, clzName, callee.Parameters, callee.FunctionName, pkgName)
        return methodId?.orElseGet { saveCalleeMethod(callee) }
    }

    private fun saveCalleeMethod(m: CodeCall): String {
        return doSaveCalleeMethod(m)
    }

    private fun doSaveCalleeMethod(m: CodeCall): String {
        val mId = generateId()
        val time: String = currentTime
        val values: MutableMap<String, String> = HashMap()
        values["id"] = mId
        values["system_id"] = systemId
        values["clzname"] = "${m.Package}.${m.NodeName}"
        values["name"] = m.FunctionName
        values["returntype"] = ""
        values["argumenttypes"] = m.Parameters.map { it.TypeType }.joinToString(",")
        values["access"] = "public"
        values["module"] = DEFAULT_MODULE_NAME
        values["package_name"] = m.Package
        values["class_name"] = m.NodeName
        values["updatedAt"] = time
        values["createdAt"] = time
        values["is_test"] = "false"
        values["loc"] = (m.Position.StopLine - m.Position.StartLine).toString()
        batch.add("code_method", values)
        return mId
    }


    private fun findMethodIdByClzName(m: CodeFunction, clzName: String, funcName: String, pkgName: String): Optional<String?>? {
        return findMethodId(DEFAULT_MODULE_NAME, clzName, m.Parameters, funcName, pkgName)
    }

    private fun findCalleeMethodId(
        module: String,
        clzName: String,
        parameters: Array<CodeProperty>,
        functionName: String,
        pkgName: String,
    ): Optional<String?>? {
        if (module.isNotEmpty()) {
            return findMethodId(module, clzName, parameters, functionName, pkgName)
        }

        return findMethodId(THIRD_PARTY, clzName, parameters, functionName, pkgName)
    }

    private fun findMethodId(
        moduleName: String,
        clzName: String,
        parameters: Array<CodeProperty>,
        callNodeName: String,
        pkgName: String
    ): Optional<String?>? {
        val keys: MutableMap<String, String> = HashMap()
        keys["clzname"] = "$pkgName.$clzName"
        keys["name"] = callNodeName
        keys["module"] = moduleName
        keys["argumenttypes"] = Json.encodeToString(parameters)
        return batch.findId("code_method", keys)
    }

    private fun saveClassParent(clzId: String, module: String, imports: Array<CodeImport>, extend: String) {
        var delimiters = "."
        if (isJs()) {
            delimiters = "/"
        }

        val imp = imports.filter { it.Source.split(delimiters).last() == extend }
        var moduleName = module
        if (imp.isNotEmpty()) {
            moduleName = imp[0].Source
        }

        val parentId = saveOrGetDependentClass(extend, moduleName)
        if (parentId != null) {
            doSaveClassParent(clzId, parentId)
        }
    }

    private fun doSaveClassParent(clzId: String, parentClzId: String) {
        val values: MutableMap<String, String> = HashMap()
        values["id"] = generateId()
        values["system_id"] = systemId
        values["a"] = clzId
        values["b"] = parentClzId
        batch.add("code_ref_class_parent", values)
    }

    private fun saveClassDependencies(
        clzId: String,
        imports: Array<CodeImport>,
        packageName: String,
        clzName: String,
        filePath: String,
        clzFunctions: Array<CodeFunction>
    ) {
        for (import in imports) {
            if (isJs()) {
                var sourceName = packageName

                var importSource = import.Source
                importSource = convertTypeScriptImport(importSource, filePath)

                val mayBeComponent = packageName.endsWith(".index") && clzName == "default"
                if (mayBeComponent) {
                    val functions = clzFunctions.filter { it.IsReturnHtml }
                    val isComponent = functions.isNotEmpty()
                    if (isComponent) {
                        sourceName = packageName.removeSuffix(".index")
                    }
                }

                val clzDependenceId = saveOrGetDependentClass(importSource, DEFAULT_MODULE_NAME)
                doSaveClassDependence(clzId, clzDependenceId, sourceName, importSource)
            } else {
                val sourceName = "${packageName}.${clzName}"

                val importSource = import.Source
                val clzDependenceId = saveOrGetDependentClass(importSource, DEFAULT_MODULE_NAME)
                doSaveClassDependence(clzId, clzDependenceId, sourceName, importSource)
            }
        }
    }

    private fun convertTypeScriptImport(importSource: String, filePath: String): String {
        var imp = importSource
        if (!imp.startsWith("@")) {
            imp = importConvert(filePath, imp)
            if (imp.startsWith("src/")) {
                imp = imp.replaceFirst("src/", "@/")
            }
        }

        imp = imp.replace("/", ".")
        return imp
    }

    private fun isJs() = language == "typescript" || language == "javascript"

    private fun saveOrGetDependentClass(name: String, moduleName: String = DEFAULT_MODULE_NAME): String? {
        // own module
        var idOpt = findClass(name, moduleName)
        if (idOpt.isPresent) {
            return idOpt.get()
        }

        //third-party
        idOpt = findClass(name, THIRD_PARTY)
        if (idOpt.isPresent) {
            return idOpt.get()
        }

        val index: Int = name.lastIndexOf(".")
        val packageName: String? = if (index < 0) null else name.substring(0, index)
        val className: String = if (index < 0) name else name.substring(index + 1)

        if (name.isEmpty() && className.isEmpty() && packageName.isNullOrEmpty()) {
            return null
        }

        // module name empty for third-part deps
        return doSaveClass(
            name,
            THIRD_PARTY,
            "",
            thirdparty = true,
            isTest = false,
            packageName = packageName,
            className = className
        )
    }

    private fun doSaveClassDependence(clzId: String, clzDependenceId: String?, sourceName: String, name: String) {
        val values: MutableMap<String, String> = HashMap()
        values["id"] = generateId()
        values["system_id"] = systemId
        values["a"] = clzId
        values["b"] = clzDependenceId.orEmpty()
        values["source"] = sourceName
        values["target"] = name
        batch.add("code_ref_class_dependencies", values)
    }

    private fun findClass(name: String, module: String?): Optional<String?> {
        val keys: MutableMap<String, String> = HashMap()
        keys["name"] = name
        if (module != null) {
            keys["module"] = module
        }

        return batch.findId("code_class", keys)
    }

    private fun saveClassMethods(clzId: String, functions: Array<CodeFunction>, clzName: String, pkgName: String) {
        for (method in functions) {
            if (clzName.isEmpty() && pkgName.isEmpty()) {
                continue
            }

            val methodId = doSaveMethod(clzName, method, pkgName)
            doSaveClassMethodRelations(clzId, methodId)

            for (localVariable in method.LocalVariables) {
                saveMethodField(methodId, localVariable, clzName)
            }

            method.Annotations.forEach {
                doSaveAnnotation(it, methodId)
            }
        }
    }

    private fun saveMethodField(methodId: String, localVariable: CodeProperty, clzName: String) {
        val fieldIdOpt = findFieldId(localVariable, clzName)
        if (!fieldIdOpt!!.isPresent) {
            return
        }

        val fieldId = fieldIdOpt.get()
        val methodFields: MutableMap<String, String> = HashMap()
        methodFields["id"] = generateId()
        methodFields["system_id"] = systemId
        methodFields["a"] = methodId
        methodFields["b"] = fieldId
        batch.add("code_ref_class_fields", methodFields)
    }

    private fun findFieldId(field: CodeProperty, clzName: String): Optional<String?>? {
        val keys: MutableMap<String, String> = HashMap()
        keys["clzname"] = clzName
        keys["name"] = field.TypeValue
        return batch.findId("code_field", keys)
    }

    private fun doSaveClassMethodRelations(clzId: String, mId: String) {
        val values: MutableMap<String, String> = HashMap()
        values["id"] = generateId()
        values["system_id"] = systemId
        values["a"] = clzId
        values["b"] = mId
        batch.add("code_ref_class_methods", values)
    }

    private fun doSaveAnnotation(annotation: CodeAnnotation, methodId: String) {
        val id = generateId()
        val values: MutableMap<String, String> = HashMap()
        values["id"] = id
        values["system_id"] = systemId
        values["targetType"] = "todo"
        values["targetId"] = methodId
        values["name"] = annotation.Name

        batch.add("code_annotation", values)

        for (keyValue in annotation.KeyValues) {
            doSaveAnnotationValue(id, keyValue)
        }
    }

    private fun doSaveAnnotationValue(annotationId: String, map: AnnotationKeyValue) {
        val id = generateId()
        val values: MutableMap<String, String> = HashMap()
        values["id"] = id
        values["system_id"] = systemId
        values["annotationId"] = annotationId
        values["key"] = map.Key
        var value = map.Value
        if (value.contains("'")) {
            value = value.replace("'", "''")
        }
        values["value"] = value

        batch.add("code_annotation_value", values)
    }

    private fun doSaveMethod(clzName: String, m: CodeFunction, pkgName: String): String {
        val mId = generateId()
        val time: String = currentTime
        val values: MutableMap<String, String> = HashMap()
        values["id"] = mId
        values["system_id"] = systemId
        values["clzname"] = "$pkgName.$clzName"
        values["name"] = m.Name
        values["returntype"] = m.ReturnType
        var arguments = m.Parameters.map { it.TypeType }.joinToString(",")
        if (arguments.contains("'")) {
            arguments = arguments.replace("'", "''")
        }
        values["argumenttypes"] = arguments

        if (m.Modifiers.isNotEmpty()) {
            values["access"] = m.Modifiers[0]
        } else {
            values["access"] = "public"
        }

        values["module"] = DEFAULT_MODULE_NAME
        values["package_name"] = pkgName
        values["class_name"] = clzName
        values["updatedAt"] = time
        values["createdAt"] = time
        values["is_test"] = if (m.isJUnitTest()) "true" else "false"
        values["loc"] = (m.Position.StopLine - m.Position.StartLine).toString()
        batch.add("code_method", values)
        return mId
    }

    private fun saveClassFields(clzId: String, fields: Array<CodeField>, clzName: String) {
        for (field in fields) {
            val id = generateId()
            val time: String = currentTime
            val values: MutableMap<String, String> = HashMap()

            // for TypeScript
            var name = field.TypeValue
            if (field.TypeValue.contains("'")) {
                name = field.TypeValue.replace("'", "''")
            }
            if (field.TypeValue.contains("\n")) {
                name = field.TypeValue.replace("\n", "\\\n")

                println(field.TypeValue)
                println("field: ${field.TypeValue} contains \\\n will translate to '\\\\\n': --------------\n$name\n-------------")
            }

            values["id"] = id
            values["system_id"] = systemId
            values["name"] = name
            values["clzname"] = clzName
            var valueType = field.TypeType
            if (valueType.contains("'")) {
                valueType = valueType.replace("'", "''")
            }
            values["type"] = valueType

            values["updatedAt"] = time
            values["createdAt"] = time
            batch.add("code_field", values)

            val relation: MutableMap<String, String> = HashMap()
            relation["id"] = generateId()
            relation["system_id"] = systemId
            relation["a"] = clzId
            relation["b"] = id
            batch.add("code_ref_class_fields", relation)
        }
    }

    private fun doSaveClass(
        name: String, module: String, access: String, thirdparty: Boolean, isTest: Boolean,
        packageName: String?, className: String
    ): String {
        val time: String = currentTime
        val clzId = generateId()
        val values: MutableMap<String, String> = HashMap()
        values["id"] = clzId
        values["system_id"] = systemId
        values["name"] = name
        values["is_thirdparty"] = if (thirdparty) "true" else "false"
        values["is_test"] = if (isTest) "true" else "false"
        values["updatedAt"] = time
        values["createdAt"] = time
        values["module"] = module
        values["package_name"] = packageName.orEmpty()
        values["class_name"] = className
        values["access"] = access

        batch.add("code_class", values)
        return clzId
    }

    private fun saveClass(clz: CodeDataStruct): String {
        val time = currentTime
        val clzId = generateId()
        val values: MutableMap<String, String> = HashMap()
        var pkgName = clz.Package
        var clzName = clz.NodeName

        if (isJs()) {
            val mayBeAComponent = pkgName.endsWith(".index") && clzName == "default"
            if (mayBeAComponent) {
                val functions = clz.Functions.filter { it.IsReturnHtml }
                val isAComponent = functions.isNotEmpty()
                if (isAComponent) {
                    pkgName = pkgName.removeSuffix(".index")
                    clzName = functions[0].Name
                }
            }
        }

        values["id"] = clzId
        values["system_id"] = systemId
        values["name"] = "$pkgName.$clzName"

        values["loc"] = (clz.Position.StopLine - clz.Position.StartLine).toString()

        values["is_thirdparty"] = "false"
        values["is_test"] = "false"
        values["updatedAt"] = time
        values["createdAt"] = time
        values["module"] = DEFAULT_MODULE_NAME
        values["package_name"] = pkgName
        values["class_name"] = clzName
        values["access"] = "todo"
        batch.add("code_class", values)

        return clzId
    }

    fun findId(table: String, keys: Map<String, String>): Optional<String>? {
        return batch.findId(table, keys)
    }

    private fun flush() {
        batch.execute()
    }

    fun close() {
        batch.execute()
        batch.close()
    }
}