package io.vrap.codegen.kt.languages.java.plantuml

import com.google.inject.Inject
import io.vrap.codegen.kt.languages.java.extensions.EObjectTypeExtensions
import io.vrap.codegen.kt.languages.java.extensions.ObjectTypeExtensions
import io.vrap.codegen.kt.languages.java.extensions.simpleName
import io.vrap.rmf.codegen.kt.io.TemplateFile
import io.vrap.rmf.codegen.kt.rendring.FileProducer
import io.vrap.rmf.codegen.kt.rendring.utils.escapeAll
import io.vrap.rmf.codegen.kt.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.kt.types.VrapObjectType
import io.vrap.rmf.codegen.kt.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.Property
import io.vrap.rmf.raml.model.types.StringInstance
import io.vrap.rmf.raml.model.types.StringType
import io.vrap.rmf.raml.model.util.StringCaseFormat

class PlantUmlDiagramProducer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : ObjectTypeExtensions, EObjectTypeExtensions, FileProducer {

    @Inject
    lateinit var allObjectTypes: MutableList<ObjectType>

    @Inject
    lateinit var allStringTypes: MutableList<StringType>


    override fun produceFiles(): List<TemplateFile> = listOf(
            TemplateFile(relativePath = "diagram.puml",
                    content = """
                    |@startuml
                    |
                    |${allStringTypes.map { it.plantUmlEnumDef() }.joinToString(separator = "\n")}
                    |
                    |${allObjectTypes.map { it.plantUmlClassDef() }.joinToString(separator = "\n")}
                    |
                    |${allObjectTypes.map { it.modelInheritenceRelation() }.joinToString(separator = "\n")}
                    |
                    |${allObjectTypes.map { it.modelCompositionRelations() }.joinToString(separator = "\n")}
                    |@enduml
                    """.trimMargin()
            ))


    fun ObjectType.modelCompositionRelations(): String {

        val vrapType = this.toVrapType()
        return this.properties
                .filter { it.type is ObjectType }
                .map {
                    "${vrapType.simpleName()} \"1\"${PlantUmlRelations.COMPOSITION} \"${it.name}\" ${it.type.toVrapType().simpleName()}"
                }
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n\n")
    }

    fun ObjectType.modelInheritenceRelation(): String {

        return this.subTypes.map {
            """|
                |${this.toVrapType().simpleName()} ${PlantUmlRelations.INHERITS} ${it.toVrapType().simpleName()}
            """.trimMargin()
        }.joinToString(separator = "\n")

    }


    fun ObjectType.plantUmlClassDef(): String {
        val vrapType = this.toVrapType() as VrapObjectType

        return """
            |package ${vrapType.`package`}{
            |   class ${vrapType.simpleClassName} {
            |       <${this.plantUmlProperties().escapeAll()}>
            |   }
            |}
        """.trimMargin().keepIndentation()

    }

    fun ObjectType.plantUmlProperties(): String {
        return this.properties
                .filter { it.type !is ObjectType }
                .map { it.plantUmlAttribute() }
                .joinToString(separator = "\n")
    }

    fun Property.plantUmlAttribute(): String {

        val vrapType = this.type.toVrapType()

        return if (this.isPatternProperty()) {
            "Map<String,${this.type.toVrapType().simpleName()}> values"
        } else {
            "${vrapType.simpleName()} ${this.name}"
        }

    }


    fun StringType.plantUmlEnumDef(): String {
        val vrapType = this.toVrapType() as VrapObjectType
        return """
            |package ${vrapType.`package`} {
            |   enum ${vrapType.simpleClassName}{
            |       <${this.enumFields()}>
            |   }
            |}
        """.trimMargin().keepIndentation()
    }

    fun StringType.enumFields(): String = this.enumJsonNames()
            .map {
                """
                |${it.enumValueName()}
            """.trimMargin()
            }
            .joinToString(separator = "\n", postfix = "")


    fun StringType.enumJsonNames() = this.enum?.filter { it is StringInstance }
            ?.map { it as StringInstance }
            ?.map { it.value }
            ?.filterNotNull() ?: listOf()

    fun String.enumValueName(): String {
        return StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(this)
    }

    fun Property.isPatternProperty() = this.name.startsWith("/") && this.name.endsWith("/")

}