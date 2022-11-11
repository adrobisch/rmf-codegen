package io.vrap.codegen.languages.rust.model

import io.vrap.codegen.languages.extensions.getSuperTypes
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.extensions.sortedByTopology
import io.vrap.codegen.languages.rust.RustObjectTypeExtensions
import io.vrap.codegen.languages.rust.exportName
import io.vrap.codegen.languages.rust.rustGeneratedComment
import io.vrap.codegen.languages.rust.rustModuleFileName
import io.vrap.codegen.languages.rust.toBlockComment
import io.vrap.codegen.languages.rust.toLineComment
import io.vrap.rmf.codegen.di.AllAnyTypes
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendering.FileProducer
import io.vrap.rmf.codegen.rendering.utils.escapeAll
import io.vrap.rmf.codegen.rendering.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapEnumType
import io.vrap.rmf.codegen.types.VrapScalarType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.*

class RustFileProducer constructor(
        override val vrapTypeProvider: VrapTypeProvider,
        @AllAnyTypes val allAnyTypes: List<AnyType>,
        @BasePackageName val basePackageName: String
) : RustObjectTypeExtensions, FileProducer {

    override fun produceFiles(): List<TemplateFile> {
        return allAnyTypes.filter { it is ObjectType || (it is StringType && it.pattern == null) }
                .groupBy {
                    it.moduleName()
                }
                .map { entry: Map.Entry<String, List<AnyType>> ->
                    buildModule(entry.key, entry.value)
                }
                .toList()
    }

    private fun buildModule(moduleName: String, types: List<AnyType>): TemplateFile {
        var sortedTypes = types.sortedByTopology(AnyType::getSuperTypes)
        val modules = getImports(sortedTypes)
                .map {
                    "use $it;"
                }

        val importExpr = if (modules.size > 0) modules.joinToString(separator = "\n") else ""

        val content = """
           |$rustGeneratedComment
           |
           |<$importExpr>
           |
           |${sortedTypes.map { it.renderAnyType() }.joinToString(separator = "\n")}
       """.trimMargin().keepIndentation()

        var filename = moduleName.rustModuleFileName()
        return TemplateFile(content, filename + ".rs")
    }

    private fun AnyType.renderAnyType(): String {
        return when (this) {
            is ObjectType -> this.renderObjectType()
            is StringType -> this.renderStringType()
            else -> throw IllegalArgumentException("unhandled case ${this.javaClass}")
        }
    }

    private fun getImports(types: List<AnyType>): List<String> {
        val objects = types.filterIsInstance<ObjectType>()

        return objects
                .filter { it.requiresJsonModule() }
                .map<ObjectType, String> {
                    "serde::{Deserialize, Serialize}"
                }
                .plus("chrono::prelude::*")
                .plus("std::iter::Map")
                .distinct()
    }

    private fun ObjectType.requiresJsonModule(): Boolean {
        if (allProperties.all { it.isPatternProperty() }) {
            return false
        } else if (this.isDiscriminated()) {
            return false
        } else {
            if (allProperties.any { it.type is ArrayType && !it.required }) {
                return true
            }
            if (
                    allProperties
                            .map { it.type }
                            .any {
                                when (it) {
                                    is ArrayType -> it.items.isDiscriminated()
                                    is ObjectType -> it.isDiscriminated()
                                    else -> false
                                }
                            }
            ) {
                return true
            }
            if (this.discriminatorValue != null) {
                return true
            }
        }
        return false
    }

    private fun ObjectType.renderObjectType(): String {

        val isMap = allProperties.all { it.isPatternProperty() }
        if (isMap) {
            val valueType = if (allProperties.size > 0) allProperties[0].type.renderTypeExpr() else "trait"

            return """
            |<${toBlockComment().escapeAll()}>
            |type $name = Map\<String, $valueType\>;
            """.trimMargin()
        } else if (this.isDiscriminated()) {
            val interfaceExpr = """
                |<${toBlockComment().escapeAll()}>
                |trait ${name.exportName()}
            """.trimMargin()

            val discriminatorMapFunc = if (this.isDiscriminated()) this.renderDiscriminatorMapper() else ""

            return """
            |$interfaceExpr
            |
            |$discriminatorMapFunc
            """.trimMargin()
        } else {
            val structField = """
                |<${toBlockComment().escapeAll()}>
                |#[derive(Serialize, Deserialize)]
                |struct ${name.exportName()} {
                |    <${renderStructFields()}>
                |}
            """.trimMargin()

            val unmarshalFunc = if (
                    allProperties
                            .map { it.type }
                            .any {
                                when (it) {
                                    is ArrayType -> it.items.isDiscriminated()
                                    is ObjectType -> it.isDiscriminated()
                                    else -> false
                                }
                            }
            ) this.renderUnmarshalFunc() else ""

            val marshalFunc = this.renderMarshalFunc()
            val errorFunc = this.renderErrorFunc()

            return """
            |$structField
            |
            |$unmarshalFunc
            |$marshalFunc
            |$errorFunc
            """.trimMargin()
        }
    }

    fun ObjectType.renderErrorFunc(): String {
        if (!this.isErrorObject()) return ""

        val messageField = rustStructFields(true)
                .filter {
                    it.type is StringType && it.name.lowercase() == "message"
                }
                .firstOrNull()

        // Shouldn't be possible
        if (messageField == null) return ""

        return """
        |func (obj $name) Error() string {
        |    if obj.${messageField.name.exportName()} != "" {
        |        return obj.${messageField.name.exportName()}
        |    }
        |    return "unknown $name: failed to parse error response"
        |}
        """.trimMargin()
    }

    /**
     * Render MarshalJSON() func to customize the JSON serialization. This is
     * needed for two reasons:
     *  1. In case this is a discriminated value, we need to add the `discriminator` field
     *  2. There are optional slices (arrays). We remove optional values which
     *  are nil but not empty
     */
    fun ObjectType.renderMarshalFunc(): String {
        // Check if there are optional slices
        val optSlices = allProperties.any { it.type is ArrayType && !it.required }

        if (this.discriminatorValue == null && !optSlices) {
            return ""
        }

        // Use the alias approach to marshall the struct without causing and
        // endless recursive loop. Add `Action` field if discriminated
        var marshalStatement = if (this.discriminatorValue != null)
            """json.Marshal(struct {
            |        Action string `json:"${this.discriminator()}"`
            |        *Alias
            |    }{Action: "$discriminatorValue", Alias: (*Alias)(&obj)})""".trimMargin()
        else
            """json.Marshal(struct {
            |        *Alias
            |    }{Alias: (*Alias)(&obj)})""".trimMargin()

        val funcBody = if (!optSlices) {
            "return $marshalStatement"
        } else {
            val deleteStatements = allProperties
                    .filter { it.type is ArrayType && !it.required }
                    .map {
                        """
                    |   if target["${it.name}"] == nil {
                    |       delete(target, "${it.name}")
                    |   }
                    """
                    }.joinToString("\n")

            """
            |   data, err := $marshalStatement
            |   if err != nil {
            |       return nil, err
            |   }
            |
            |   target := make(map[string]interface{})
            |   if err := json.Unmarshal(data, &target); err != nil {
            |       return nil, err
            |   }
            |
            |   $deleteStatements
            |
            |   return json.Marshal(target)
            """.trimMargin()
        }

        return """
        |// MarshalJSON override to set the discriminator value or remove
        |// optional nil slices
        |func (obj $name) MarshalJSON() ([]byte, error) {
        |    type Alias $name
        |    $funcBody
        |}
        """.trimMargin()
    }

    fun ObjectType.renderUnmarshalFuncFields(defaultRetval: String = ""): String {
        val retval = if (defaultRetval.isNotEmpty()) "$defaultRetval, err" else "err"
        return allProperties
                .filter {
                    val type = it.type
                    when (type) {
                        is ArrayType -> {
                            type.items.isDiscriminated()
                        }

                        is ObjectType -> type.isDiscriminated()
                        else -> false
                    }
                }
                .map {
                    val attrName = it.name.exportName()
                    val type = it.type
                    when (type) {
                        is ArrayType -> {
                            val typeName = type.items.name.exportName()
                            """
                        |for i := range obj.$attrName {
                        |    var err error
                        |    obj.$attrName[i], err = mapDiscriminator$typeName(obj.$attrName[i])
                        |    if err != nil {
                        |        return $retval
                        |    }
                        |}
                        """.trimMargin()
                        }

                        is ObjectType -> {
                            val typeName = type.name.exportName()
                            """
                        |if obj.$attrName != nil {
                        |    var err error
                        |    obj.$attrName, err = mapDiscriminator$typeName(obj.$attrName)
                        |    if err != nil {
                        |        return $retval
                        |    }
                        |}
                        """.trimMargin()
                        }

                        else -> ""
                    }
                }
                .joinToString("\n")
    }

    fun ObjectType.renderUnmarshalFunc(): String {
        val fieldHandlers = this.renderUnmarshalFuncFields()
        return """
        |// UnmarshalJSON override to deserialize correct attribute types based
        |// on the discriminator value
        |func (obj *${name.exportName()}) UnmarshalJSON(data []byte) error {
        |    type Alias ${name.exportName()}
        |    if err := json.Unmarshal(data, (*Alias)(obj)); err != nil {
        |        return err
        |    }
        |    $fieldHandlers
        |    return nil
        |}
        """.trimMargin()
    }

    fun ObjectType.renderDiscriminatorMapper(): String {

        val caseStatements = allAnyTypes.getTypeInheritance(this)
                .filterIsInstance<ObjectType>()
                .filter { !it.discriminatorValue.isNullOrEmpty() }
                .map {
                    val extraAttrs = it.renderUnmarshalFuncFields("nil")
                    """
            |   case "${it.discriminatorValue}":
            |       obj := ${it.name}{}
            |       if err := decodeStruct(input, &obj); err != nil {
            |           return nil, err
            |       }
            |       <$extraAttrs>
            |       return obj, nil
            """.trimMargin()
                }
                .joinToString("\n")

        return """
            |func mapDiscriminator$name(input interface{}) ($name, error) {
            |    var discriminator string
            |    if data, ok := input.(map[string]interface{}); ok {
            |        discriminator, ok = data["${this.discriminator()}"].(string)
            |        if !ok {
            |            return nil, errors.New("error processing discriminator field '${this.discriminator()}'")
            |        }
            |    } else {
            |        return nil, errors.New("invalid data")
            |    }
            |
            |    switch discriminator {
            |    $caseStatements
            |    }
            |    return nil, nil
            |}
        """.trimMargin()
    }

    // Renders the attribute of this model as type annotations.
    fun ObjectType.renderStructFields(): String {
        return rustStructFields(true)
                .map {
                    val comment: String = it.type.toLineComment().escapeAll()
                    val name = it.name

                    if (it.required) {
                        """
                    |<$comment>
                    |#[serde(rename = "${it.name}")]
                    |${name.exportName()}: ${it.type.renderTypeExpr()}""".trimMargin()
                    } else {
                        val type = it.type

                        """
                    |<$comment>
                    |#[serde(rename = "${it.name}")]
                    |${name.exportName()}: Option\<${type.renderTypeExpr()}\>""".trimMargin()
                    }
                }
                .joinToString(",\n")
    }

    private fun StringType.renderStringType(): String {
        val vrapType = this.toVrapType()

        return when (vrapType) {
            is VrapEnumType ->
                return """
                |<${toBlockComment().escapeAll()}>
                |type ${vrapType.simpleClassName.exportName()} string
                |
                |<${this.renderEnumValues(vrapType.simpleClassName)}>
                |
                """.trimMargin()

            is VrapScalarType -> """
                |type ${this.name} = ${vrapType.scalarType}
            """.trimMargin()

            else -> ""
        }
    }

    private fun StringType.renderEnumValues(enumName: String): String {
        return this.enumValues()
                .map { "${enumName}${it.exportName()} $enumName = \"$it\"" }
                .joinToString(prefix = "const (", separator = "\n", postfix = ")")
    }

    private fun StringType.enumValues() = enum?.filter { it is StringInstance }
            ?.map { (it as StringInstance).value }
            ?.filterNotNull() ?: listOf()
}
