package io.vrap.codegen.languages.csharp.model

import com.google.inject.Inject
import io.vrap.codegen.languages.csharp.extensions.*
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.codegen.languages.extensions.discriminatorProperty
import io.vrap.codegen.languages.extensions.hasSubtypes
import io.vrap.codegen.languages.extensions.namedSubTypes
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.ObjectTypeRenderer
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapArrayType
import io.vrap.rmf.codegen.types.VrapEnumType
import io.vrap.rmf.codegen.types.VrapObjectType
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.Property
import io.vrap.rmf.raml.model.types.impl.ObjectTypeImpl


class CsharpObjectTypeRenderer @Inject constructor(override val vrapTypeProvider: VrapTypeProvider) : CsharpObjectTypeExtensions, EObjectExtensions, ObjectTypeRenderer {


    override fun render(type: ObjectType): TemplateFile {
        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType

        var content : String = """
            |${type.usings()}
            |
            |namespace ${vrapType.csharpPackage()}
            |{
            |    <${type.discriminatorAttribute()}>
            |    <${type.discriminatorValueAttribute()}>
            |    public${ if(type.isAbstract())" abstract" else ""} partial class ${vrapType.simpleClassName} ${type.type?.toVrapType()?.simpleName()?.let { ": $it" } ?: ""}
            |    {
            |        <${type.toProperties()}>
            |        <${type.renderConstructor()}>
            |    }
            |}
            |
        """.trimMargin().keepIndentation()


        if(type.isADictionaryType())
        {
            content = type.renderTypeAsADictionaryType().trimMargin().keepIndentation()
        }

        return TemplateFile(
                relativePath = vrapType.csharpClassRelativePath(),
                content = content
        )
    }

    private fun ObjectType.toProperties() : String = this.properties
            .map { it.toCsharpProperty(this) }.joinToString(separator = "\n\n")

    private fun Property.toCsharpProperty(objectType: ObjectType): String {

        val isEnumProp = this.type.toVrapType() is VrapEnumType
        val isListOfEnumProp = this.type.toVrapType() is VrapArrayType && (this.type.toVrapType() as VrapArrayType).itemType is VrapEnumType
        val propName = this.name.capitalize()
        val typeName = this.type.toVrapType().simpleName()

        if(isEnumProp || isListOfEnumProp)
        {
            val pType = if (isEnumProp) "string" else """List\<string\>"""
            val pEnumType = if(isEnumProp) typeName else (this.type.toVrapType() as VrapArrayType).itemType.simpleName()
            return  """public $pType $propName { get; set;}
                        |
                        |[JsonIgnore]
                        |public $typeName ${propName}AsEnum =\> this.$propName.GetEnum\<$pEnumType\>();"""
        }

        var nullableChar = if(!this.required && this.type.toVrapType().isValueType()) "?" else ""
        return "public ${typeName}$nullableChar $propName { get; set;}"
    }

    fun ObjectType.renderTypeAsADictionaryType() : String {
        val vrapType = vrapTypeProvider.doSwitch(this) as VrapObjectType

        var property = this.properties[0]

        return  """
            |${this.usings()}
            |
            |namespace ${vrapType.csharpPackage()}
            |{
            |    public class ${toVrapType().simpleName()} : Dictionary\<string, ${property.type.toVrapType().simpleName()}\>
            |    {
            |        <${this.renderConstructor()}>
            |    }
            |}
            |
        """
    }
    fun ObjectType.renderConstructor() : String {
        var isEmptyConstructor = this.getConstructorContentForDiscriminator() == "";
        return if(!isEmptyConstructor)
            """public ${toVrapType().simpleName()}()
                |{ 
                |${this.getConstructorContentForDiscriminator()}
                |}"""
        else
            ""
        }
    fun ObjectType.discriminatorAttribute(): String {
        return if (hasSubtypes())
            """
            |[Discriminator(nameof(${this.discriminator.capitalize()}))]
            """.trimMargin()
        else
            ""
    }
    fun ObjectType.discriminatorValueAttribute(): String {
        return if (discriminatorValue != null)
            """
            |[DiscriminatorValue("${this.discriminatorValue}")]
            """.trimMargin()
        else
            ""
    }
    fun ObjectType.getConstructorContentForDiscriminator(): String {
        return if (discriminatorValue != null)
            """
            |   this.${(this.type as ObjectTypeImpl).discriminator.capitalize()} = "${this.discriminatorValue}";
            """.trimMargin()
        else
            ""
    }
}
