package org.archguard.scanner.analyser.backend.go

import chapi.domain.core.CodeCall
import chapi.domain.core.CodeDataStruct
import chapi.domain.core.CodeFunction
import chapi.domain.core.CodeImport
import org.archguard.context.ContainerDemand
import java.io.File

class GoProtobufConsumerAnalyser {
    private val dataStructs: List<CodeDataStruct>
    private val workspace: String
    private val parentSpace: String
    private val fullPathMap: Map<String, List<CodeDataStruct>>
    private var packageDsMap: Map<String, List<CodeDataStruct>>

    constructor(dataStructs: List<CodeDataStruct>, workspace: String) {
        this.parentSpace = File(workspace).parent
        dataStructs.forEach {
            it.FilePath = it.FilePath.replace(parentSpace, "").removePrefix("/")
        }
        this.dataStructs = dataStructs

        this.workspace = workspace
        this.fullPathMap = dataStructs.groupBy {
            it.FilePath.split(".").dropLast(1).joinToString("/")
        }

        this.packageDsMap = dataStructs.groupBy {
            it.FilePath.split("/").dropLast(1).joinToString("/")
        }
    }

    fun analysis(): List<ContainerDemand> {
        val singleMapping: MutableMap<String, List<String>> = analyzeAndMapCodePaths(dataStructs)
        if (singleMapping.isEmpty()) return listOf()

        val result: MutableList<ContainerDemand> = mutableListOf()
        singleMapping.filter { it.key.startsWith("RPC") && !it.key.contains("/") }.map {
            val call = buildCallChain(singleMapping, it.key)
            result += ContainerDemand(
                source_caller = call.last(), call_routes = call, target_url = call.first(), target_http_method = "RPC"
            )
        }

        return result
    }

    /// if start with RPC. try to find the call routers => iterate the value, and value as key to find the next value
    /// if  classB.methodB call classA.methodA, RPC.someMethod, then the mapping should be
    //  - "RPC.someMethod" <-- classA.methodA
    /// - classA.methodA <-- classB.methodB
    /// But the result should be ["RPC.someMethod", classA.methodA, classB.methodB]
    fun buildCallChain(reverseMapping: MutableMap<String, List<String>>, target: String): List<String> {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()

        fun dfs(node: String) {
            if (visited.contains(node)) {
                return
            }

            visited.add(node)
            result.add(node)

            reverseMapping[node]?.forEach {
                dfs(it)
            }
        }

        dfs(target)
        return result
    }

    fun analyzeAndMapCodePaths(codeDataStructs: List<CodeDataStruct>): MutableMap<String, List<String>> {
        val currentDsMap = codeDataStructs.groupBy {
            it.NodeName
        }

        val importMap: MutableMap<String, CodeImport> = mutableMapOf()
        val targetToSource: MutableMap<String, List<String>> = mutableMapOf()

        codeDataStructs.forEach { ds ->
            val imports = ds.Imports
            imports.forEach { codeImport ->
                codeImport.UsageName.forEach {
                    importMap[it] = codeImport
                }
            }

            ds.Functions.forEach { function ->
                function.FunctionCalls.forEach { call ->
                    handleSpecCall(call, targetToSource, ds, function)

                    if (call.NodeName.contains(".") && !call.NodeName.contains(".client")) {
                        val split = call.NodeName.split(".")
                        val struct = split.first()
                        val model = split.drop(1).joinToString(".")

                        val fieldStruct = currentDsMap[struct] ?: return@forEach
                        var serviceField =
                            fieldStruct.map {
                                it.Fields.filter { field -> field.TypeValue == model }
                            }
                                .flatten()

                        /// lookup current dir to find the service
                        if (serviceField.isEmpty()) {
                            // get parent
                            val parent = ds.FilePath.split("/").dropLast(1).joinToString("/")
                            val underPackageDs = packageDsMap[parent]
                            underPackageDs?.forEach { parentDs ->
                                val parentStruct = parentDs.Fields.filter { field -> field.TypeValue == model }
                                if (parentStruct.isNotEmpty()) {
                                    serviceField = parentStruct
                                }
                            }
                        }

                        serviceField.forEach { codeField ->
                            val typeType = codeField.TypeType.removePrefix("*")
                            val importPath = typeType.split(".").first()

                            importPath.let {
                                val import = importMap[it]
                                val targetFile = packageDsMap[import?.Source ?: return@let]
                                val source = pathify(ds, function)
                                val callName = call.FunctionName

                                targetFile?.forEach { ds ->
                                    ds.Functions.forEach { targetFunction ->
                                        if (targetFunction.Name == callName) {
                                            targetToSource[pathify(ds, targetFunction)] = listOf(source)
                                        }
                                    }
                                }
                            }
                        }
                    } else if ((call.NodeName == "Service.client") && call.FunctionName == "Call") {
                        if (imports.any { it.Source.contains("net/rpc") }) {
                            if (call.Parameters.size > 1) {
                                val secondCall = call.Parameters[1]
                                targetToSource[secondCall.TypeValue.removeSurrounding("\"")] =
                                    listOf(pathify(ds, function))
                            }
                        }
                    }
                }
            }
        }

        return targetToSource
    }

    /**
     * TODO()  refactor this to go through the code
     * ```go
     * func (c *bubbleRPCClient) MultiAdd(ctx context.Context, in *BubbleMultiAddReq, opts ...liverpc.CallOption) (*BubbleMultiAddResp, error) {
     * 	out := new(BubbleMultiAddResp)
     * 	err := doRPCRequest(ctx, c.client, 1, "Bubble.multiAdd", in, out, opts)
     * 	if err != nil {
     * 		return nil, err
     * 	}
     * 	return out, nil
     * }
     *
     * func doRPCRequest(ctx context.Context, client *liverpc.Client, version int, method string, in, out proto.Message, opts []liverpc.CallOption) (err error) {
     * 	err = client.Call(ctx, version, method, in, out, opts...)
     * 	return
     * }
     * ```
     */
    private fun handleSpecCall(
        call: CodeCall, targetToSource: MutableMap<String, List<String>>, ds: CodeDataStruct, function: CodeFunction
    ) {
        if (call.NodeName == "doRPCRequest") {
            call.Parameters.getOrNull(3)?.let {
                targetToSource[it.TypeValue.removeSurrounding("\"")] = listOf(pathify(ds, function))
            }
        }
    }

    private fun pathify(ds: CodeDataStruct, function: CodeFunction) =
        ds.FilePath.split(".").dropLast(1).joinToString("/") + "$" + ds.NodeName + "." + function.Name
}