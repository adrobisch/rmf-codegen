package io.vrap.codegen.languages.csharp.requests

import com.google.inject.Inject
import io.vrap.codegen.languages.csharp.extensions.*
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.codegen.languages.extensions.resource
import io.vrap.codegen.languages.extensions.toRequestName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.MethodRenderer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapScalarType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.impl.ResourceImpl
import io.vrap.rmf.raml.model.types.QueryParameter
import io.vrap.rmf.raml.model.util.StringCaseFormat
import org.eclipse.emf.ecore.EObject

const val PLACEHOLDER_PARAM_ANNOTATION = "placeholderParam"

class CsharpHttpRequestRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : MethodRenderer, CsharpObjectTypeExtensions, EObjectExtensions {

    override fun render(type: Method): TemplateFile {


        val vrapType = vrapTypeProvider.doSwitch(type as EObject) as VrapObjectType

        val entityFolder = (type.resource() as ResourceImpl).GetNameAsPlurar()

        if(type.toRequestName() == "ByProjectKeyProductsByIDImagesPost")
        {
            var x =1
        }

        val content = """
            |using System;
            |using System.IO;
            |using System.Collections.Generic;
            |using System.Net.Http;
            |using System.Threading.Tasks;
            |using System.Text.Json;
            |using commercetools.Api.Serialization;
            |${type.usings()}
            |
            |namespace ${vrapType.requestBuildersPackage(entityFolder)}
            |{
            |   public partial class ${type.toRequestName()} : ApiMethod\<${type.toRequestName()}\> {
            |
            |       <${type.properties()}>
            |   
            |       <${type.constructor()}>
            |   
            |       <${type.queryParamsGetters()}>
            |   
            |       <${type.queryParamsSetters()}>
            |
            |       <${type.executeAndBuild()}>
            |   }
            |}
        """.trimMargin()
                .keepIndentation()

        return TemplateFile(
                relativePath = "${vrapType.requestBuildersPackage(entityFolder)}.${type.toRequestName()}".replace(".", "/") + ".cs",
                content = content
        )
    }


    private fun Method.properties(): String? {

        var props = "private IClient ApiHttpClient { get; }"+ "\n\n" + """public override HttpMethod Method =\>\ HttpMethod.${methodName.capitalize()};"""
        //only for post methods
        if(this.methodName.toLowerCase() == "post") {
            props= "private ISerializerService SerializerService { get; }\n\n$props";
        }
        val pathArgs = props + "\n\n" + this.pathArguments().map { "private string ${it.capitalize()} { get; }" }.joinToString(separator = "\n\n")

        val body: String = if(this.bodies != null && this.bodies.isNotEmpty()){
            if(this.bodies[0].type.toVrapType() is VrapObjectType){
                val methodBodyVrapType = this.bodies[0].type.toVrapType() as VrapObjectType
                if(methodBodyVrapType.`package`=="")
                    "private ${methodBodyVrapType.simpleClassName} ${methodBodyVrapType.simpleClassName.capitalize()};"
                else
                    "private ${methodBodyVrapType.`package`.toCsharpPackage()}.${methodBodyVrapType.simpleClassName} ${methodBodyVrapType.simpleClassName.capitalize()};"
            }else {
                "private JsonElement jsonNode;"
            }
        }else{
            ""
        }

        return """|
            |<$pathArgs>
            |
            |<$body>
        """.trimMargin()
    }

    private fun Method.constructor(): String? {
        val pathArguments = this.pathArguments().map { "{$it}" }
        var requestUrl = this.resource().fullUri.template
        pathArguments.forEach { requestUrl = requestUrl.replace(it, "{"+it.replace("{","").replace("}","").capitalize()+"}") }

        val constructorArguments = mutableListOf("IClient apiHttpClient")
        val constructorAssignments = mutableListOf("this.ApiHttpClient = apiHttpClient;","this.RequestUrl = $\"${requestUrl}\";")
        //only for post methods
        if(this.methodName.toLowerCase() == "post")
        {
            constructorArguments.add("ISerializerService serializerService")
            constructorAssignments.add("this.SerializerService = serializerService;")
        }

        this.pathArguments().map { "string ${it.lowerCamelCase()}" }.forEach { constructorArguments.add(it) }
        this.pathArguments().map { "this.${it.capitalize()} = ${it.lowerCamelCase()};" }.forEach { constructorAssignments.add(it) }

        if(this.bodies != null && this.bodies.isNotEmpty()){
            if(this.bodies[0].type.toVrapType() is VrapObjectType) {
                val methodBodyVrapType = this.bodies[0].type.toVrapType() as VrapObjectType
                var methodBodyArgument =""
                if(methodBodyVrapType.`package`=="")
                    methodBodyArgument = "${methodBodyVrapType.simpleClassName} ${methodBodyVrapType.simpleClassName.decapitalize()}"
                else
                    methodBodyArgument = "${methodBodyVrapType.`package`.toCsharpPackage()}.${methodBodyVrapType.simpleClassName} ${methodBodyVrapType.simpleClassName.decapitalize()}"
                constructorArguments.add(methodBodyArgument)
                val methodBodyAssignment = "this.${methodBodyVrapType.simpleClassName.capitalize()} = ${methodBodyVrapType.simpleClassName.decapitalize()};"
                constructorAssignments.add(methodBodyAssignment)
            }else {
                constructorArguments.add("JsonElement jsonNode")
                constructorAssignments.add("this.jsonNode = jsonNode;")
            }
        }

        return """
            |public ${this.toRequestName()}(${constructorArguments.joinToString(separator = ", ")}) {
            |    <${constructorAssignments.joinToString(separator = "\n")}>
            |}
        """.trimMargin().keepIndentation()

    }

    private fun Method.pathArguments() : List<String> {
        return this.resource().fullUri.variables.toList()
    }

    private fun QueryParameter.fieldName(): String {
        return StringCaseFormat.LOWER_CAMEL_CASE.apply(this.name.replace(".", "-"))
    }

    private fun QueryParameter.fieldNameAsString(type: String): String {
        var fieldName = this.fieldName()
        if(type == "string")
            return fieldName
        else
            return "$fieldName.ToString()"
    }

    private fun Method.queryParamsGetters() : String = this.queryParameters
            .filter { it.getAnnotation(PLACEHOLDER_PARAM_ANNOTATION, true) == null }
            .map { """
                |public List<string> Get${it.fieldName().capitalize()}() {
                |    return this.GetQueryParam("${it.fieldName()}");
                |}
                """.trimMargin().escapeAll() }
            .joinToString(separator = "\n\n")

    private fun Method.queryParamsSetters() : String = this.queryParameters
            .filter { it.getAnnotation(PLACEHOLDER_PARAM_ANNOTATION, true) == null }
            .map { """
                |public ${this.toRequestName()} With${it.fieldName().capitalize()}(${it.type.toVrapType().simpleName()} ${it.fieldName()}){
                |    return this.AddQueryParam("${it.fieldName()}", ${it.fieldNameAsString(it.type.toVrapType().simpleName())});
                |}
            """.trimMargin().escapeAll() }
            .joinToString(separator = "\n\n")


    private fun Method.executeAndBuild() : String {
        var executeBlock =
                """
            |public async Task\<${this.csharpReturnType(vrapTypeProvider)}\> ExecuteAsync()
            |{
            |   var requestMessage = Build();
            |   return await ApiHttpClient.ExecuteAsync\<${this.csharpReturnType(vrapTypeProvider)}\>(requestMessage);
            |}
        """.trimMargin()

        var bodyBlock = "";
        //only for post methods
        if(this.methodName.toLowerCase() == "post")
        {
            val bodyName : String? = if(this.bodies != null && this.bodies.isNotEmpty()){
                if(this.bodies[0].type.toVrapType() is VrapObjectType) {
                    val methodBodyVrapType = this.bodies[0].type.toVrapType() as VrapObjectType
                    methodBodyVrapType.simpleClassName.capitalize()
                }else {
                    "jsonNode"
                }
            }else {
                null
            }

            bodyBlock = "\n\n" +
                    """
            |public override HttpRequestMessage Build()
            |{
            |   var request = base.Build();
            |   var body = this.SerializerService.Serialize(${bodyName});
            |   if(!string.IsNullOrEmpty(body))
            |   {
            |       request.Content = new StringContent(body);
            |   }
            |   return request;
            |}
        """.trimMargin()
        }
        return executeBlock + bodyBlock
    }

    private fun Method.usings(): String {
        return this.queryParameters
                .map {
                    it.type.toVrapType()
                }
                .filter { it !is VrapScalarType }
                .map {
                    getUsingsForType(it)
                }
                .filter { !it.isNullOrBlank() }
                .map { "using ${it};" }
                .joinToString(separator = "\n")

    }
}