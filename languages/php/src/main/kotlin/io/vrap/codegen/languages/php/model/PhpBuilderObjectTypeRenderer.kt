package io.vrap.codegen.languages.php.model

import io.vrap.codegen.languages.extensions.discriminatorProperty
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.php.ClientConstants
import io.vrap.codegen.languages.php.PhpSubTemplates
import io.vrap.codegen.languages.php.extensions.*
import io.vrap.rmf.codegen.firstUpperCase
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendering.ObjectTypeRenderer
import io.vrap.rmf.codegen.rendering.utils.escapeAll
import io.vrap.rmf.codegen.rendering.utils.keepAngleIndent
import io.vrap.rmf.codegen.rendering.utils.keepCurlyIndent
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.model.types.Annotation
import io.vrap.rmf.raml.model.util.StringCaseFormat

class PhpBuilderObjectTypeRenderer constructor(override val vrapTypeProvider: VrapTypeProvider, clientConstants: ClientConstants) : ObjectTypeExtensions, EObjectTypeExtensions, ObjectTypeRenderer {

    private val basePackagePrefix = clientConstants.basePackagePrefix

    private val sharedPackageName = clientConstants.sharedPackageName

    override fun render(type: ObjectType): TemplateFile {

        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType

        val content = when (type.getAnnotation("asMap")) {
            is Annotation -> mapContent(type)
            else -> content(type)
        }


        return TemplateFile(
                relativePath = "src/" + vrapType.fullClassName().replace(basePackagePrefix.toNamespaceName(), "").replace("\\", "/") + "Builder.php",
                content = content
        )
    }

    private fun mapContent(type: ObjectType): String {
        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType

        return """
            |<?php
            |${PhpSubTemplates.generatorInfo}
            |namespace ${vrapType.namespaceName().escapeAll()};
            |
            |use stdClass;
            |use ${sharedPackageName.toNamespaceName().escapeAll()}\\Base\\MapperMap;
            |use ${sharedPackageName.toNamespaceName().escapeAll()}\\Base\\Builder;
            |
            |/**
            | * @implements Builder<${vrapType.simpleClassName}>
            | * @extends MapperMap<${vrapType.simpleClassName}>
            | */
            |final class ${vrapType.simpleClassName}Builder extends MapperMap implements Builder
            |{
            |    /**
            |     * @psalm-return callable(string):?${vrapType.simpleClassName}
            |     */
            |    protected function mapper()
            |    {
            |        return
            |            /**
            |             * @psalm-return ?${vrapType.simpleClassName}
            |             */
            |            function(string $!key) {
            |                $!data = $!this->get($!key);
            |                if ($!data instanceof stdClass) {
            |                    $!data = ${vrapType.simpleName()}Model::of($!data);
            |                }
            |                return $!data;
            |            };
            |    }
            |    
            |    /**
            |     * @return ${vrapType.simpleClassName}
            |     */
            |    public function build()
            |    {
            |        return new ${vrapType.simpleClassName}Model($!this->toArray());
            |    }
            |}
        """.trimMargin().keepAngleIndent().forcedLiteralEscape()
    }

    fun content(type: ObjectType): String {
        val vrapType = vrapTypeProvider.doSwitch(type) as VrapObjectType

        return """
            |<?php
            |${PhpSubTemplates.generatorInfo}
            |namespace ${vrapType.namespaceName().escapeAll()};
            |
            |use ${sharedPackageName.toNamespaceName().escapeAll()}\\Base\\Builder;
            |use ${sharedPackageName.toNamespaceName().escapeAll()}\\Base\\DateTimeImmutableCollection;
            |use ${sharedPackageName.toNamespaceName().escapeAll()}\\Base\\JsonObject;
            |use ${sharedPackageName.toNamespaceName().escapeAll()}\\Base\\JsonObjectModel;
            |use ${sharedPackageName.toNamespaceName().escapeAll()}\\Base\\MapperFactory;
            |use stdClass;
            |<<${type.imports()}>>
            |
            |/**
            | * @implements Builder<${vrapType.simpleClassName}>
            | */
            |final class ${vrapType.simpleClassName}Builder implements Builder
            |{
            |    <<${type.toBeanFields()}>>
            |
            |    <<${type.getters()}>>
            |
            |    <<${type.withers()}>>
            |
            |    <<${type.withBuilders()}>>
            |    
            |    <<${type.build()}>>
            |    
            |    public static function of(): ${vrapType.simpleClassName}Builder
            |    {
            |        return new self();
            |    }
            |}
        """.trimMargin().keepAngleIndent().forcedLiteralEscape()
    }

    fun ObjectType.build(): String {
        val vrapType = this.toVrapType() as VrapObjectType
        val discriminator = this.discriminatorProperty()
        return """
                |public function build(): ${vrapType.simpleClassName}
                |{
                |    return new ${vrapType.simpleClassName}Model(
                |        <<${this.allProperties.filterNot { it.deprecated() }.filter { property -> property != discriminator }.filter { !it.isPatternProperty() }.joinToString(",\n") { it.buildProperty() }}>>
                |    );
                |}
            """.trimMargin()
    }

    fun Property.buildProperty(): String {
        val vt = this.type.toVrapType()
        if (this.type.isScalar() || this.type is ArrayType || vt.simpleName() == "stdClass" || vt.simpleName() == "mixed") {
            return "$!this->${this.name}"
        }
        return "$!this->${this.name} instanceof ${vt.simpleBuilderName()} ? $!this->${this.name}->build() : $!this->${this.name}"
    }

    fun ObjectType.imports() = this.getImports(this.allProperties).map { "use ${it.escapeAll()};" }
            .plus(this.getImports(this.allProperties.filterNot { it.deprecated() }.filter { !it.type.isScalar() && !(it.type is ArrayType) && !(it.type.toVrapType().simpleName() == "stdClass") }).map { "use ${it.escapeAll()}Builder;" })
            .distinct()
            .sorted()
            .joinToString(separator = "\n")

    fun Property.toPhpField(): String {
        if (this.type.isScalar() || this.type is ArrayType || this.type.toVrapType().simpleName() == "stdClass") {
            return """
                |/**
                |${this.deprecationAnnotation()}
                | * @var ?${if (this.type.toVrapType().simpleName() != "stdClass") this.type.toVrapType().simpleName() else "JsonObject" }
                | */
                |private $${this.name};
            """.trimMargin()
        }
        return """
            |/**
            |${this.deprecationAnnotation()}
            | * @var null|${this.type.toVrapType().simpleName()}|${this.type.toVrapType().simpleBuilderName()}
            | */
            |private $${this.name};
        """.trimMargin()
    }

    fun Property.toPhpConstantName(): String {
        return "FIELD_${StringCaseFormat.UPPER_UNDERSCORE_CASE.apply(this.patternName())}"
    }

    fun Property.toPhpConstant(): String {
        return """
            |public const ${this.toPhpConstantName()} = '${this.name}';
        """.trimMargin()
    }

    private fun Property.patternName(): String {
        return if (this.isPatternProperty())
            "pattern" + (this.eContainer() as ObjectType).properties.indexOf(this)
        else
            this.name
    }

    fun ObjectType.toBeanConstant() = this.properties.joinToString(separator = "\n") { it.toPhpConstant() }

    fun ObjectType.toBeanFields(): String {
        val discriminator = this.discriminatorProperty()

        return this.allProperties
                .filterNot { it.deprecated() }
                .filter { property -> property != discriminator }
                .filter { !it.isPatternProperty() }.joinToString(separator = "\n\n") { it.toPhpField() }
    }

    fun ObjectType.withers(): String {
        val discriminator = this.discriminatorProperty()

        return this.allProperties
                .filterNot { it.deprecated() }
                .filter { property -> property != discriminator }
                .filter { !it.isPatternProperty() }.joinToString(separator = "\n\n") { it.wither() }
    }

    fun ObjectType.withBuilders(): String {
        val discriminator = this.discriminatorProperty()

        return this.allProperties
                .filterNot { it.deprecated() }
                .filter { property -> property != discriminator }
                .filter { !it.isPatternProperty() }
                .filter { !it.type.isScalar() && !(it.type is ArrayType) && !(it.type.toVrapType().simpleName() == "stdClass") && !(it.type.toVrapType().simpleName() == "mixed") }.joinToString(separator = "\n\n") { it.withBuilder() }
    }

    fun ObjectType.getters(): String {
        val discriminator = this.discriminatorProperty()

        return this.allProperties
                .filterNot { it.deprecated() }
                .filter { property -> property != discriminator }
                .filter { !it.isPatternProperty() }.joinToString(separator = "\n\n") { it.getter() }
    }

    fun Property.wither(): String {
        val t = when(this.type.toVrapType().simpleName()) {
            "stdClass" -> "?JsonObject"
            "mixed" -> ""
            else -> "?${this.type.toVrapType().simpleName()}"
        }
        val d = when(this.type.toVrapType().simpleName()) {
            "stdClass" -> "?JsonObject"
            "mixed" -> "mixed"
            else -> "?${this.type.toVrapType().simpleName()}"
        }
        return """
            |/**
            | * @param $d $${this.name}
            | * @return $!this
            | */
            |public function with${this.name.firstUpperCase()}($t $${this.name})
            |{
            |    $!this->${this.name} = $${this.name};
            |    
            |    return $!this;
            |}
        """.trimMargin()
    }

    fun Property.withBuilder(): String {
        return """
            |/**
            | * @deprecated use with${this.name.firstUpperCase()}() instead
            | * @return $!this
            | */
            |public function with${this.name.firstUpperCase()}Builder(?${this.type.toVrapType().simpleBuilderName()} $${this.name})
            |{
            |    $!this->${this.name} = $${this.name};
            |    
            |    return $!this;
            |}
        """.trimMargin()
    }

    fun Property.getter(): String {
        return """
                |/**${if (this.type.description?.value?.isNotBlank() == true) """
                | {{${this.type.toPhpComment()}}}
                | *""" else ""}
                |${this.deprecationAnnotation()}
                | * @return null|${if (this.type.toVrapType().simpleName() != "stdClass") this.type.toVrapType().simpleName() else "JsonObject" }
                | */
                |public function get${this.name.firstUpperCase()}()
                |{
                |    return ${this.buildProperty()};
                |}
        """.trimMargin().keepCurlyIndent()
    }

    fun Property.deprecationAnnotation(): String {
        val anno = this.getAnnotation("markDeprecated", true)
        if (anno != null && (anno.value as BooleanInstance).value == true) {
            return """
                | * @deprecated""".trimMargin()
        }
        return "";
    }
}

