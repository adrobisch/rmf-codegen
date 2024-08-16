package io.vrap.codegen.languages.rust

import io.vrap.codegen.languages.extensions.ExtensionsBase
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.codegen.languages.extensions.namedSubTypes
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.types.*
import org.eclipse.emf.ecore.EObject
import java.util.*
import kotlin.collections.HashMap

interface RustObjectTypeExtensions : ExtensionsBase {
    fun isRecursive(findObjectType: ObjectType, contextTypes: List<ObjectType>): Boolean {
        if (contextTypes.find { it.toVrapType() == findObjectType.toVrapType() } !== null) {
            return true
        }

        val typesToCheck = Stack<AnyType>()
        val visited = HashMap<AnyType, Boolean>()

        findObjectType.allProperties.forEach { typesToCheck.push(it.type) }
        if (findObjectType.isDiscriminated()) {
            findObjectType.namedSubTypes().forEach { typesToCheck.push(it) }
        }

        while (!typesToCheck.isEmpty()) {
            val next = typesToCheck.pop() ?: continue

            if (visited[next] == true) {
                continue
            } else {
                visited[next] = true
            }

            if (next.toVrapType() == findObjectType.toVrapType() || contextTypes.contains(next)) {
                return true
            }

            when (next) {
                is ObjectType -> {
                    next.allProperties.forEach { typesToCheck.push(it.type) }
                    if (next.isDiscriminated()) {
                        next.namedSubTypes().forEach { typesToCheck.push(it) }
                    }
                }
                is UnionType -> typesToCheck.push(next.commonType())
            }
        }
        return false
    }

    fun AnyType.renderTypeExpr(contextTypes: List<ObjectType> = listOf()): String {
        return when (this) {
            is ObjectType -> {
                val typeName = toVrapType().rustTypeName()

                if(isRecursive(this, contextTypes)) {
                    return "Box\\<${typeName}\\>"
                } else {
                    return typeName
                }
            }
            is UnionType -> {
                if (oneOf.size > 1) {
                    when(val common = this.commonType()) {
                        is ObjectType -> return common.renderTypeExpr()
                    }
                    return "Box<dyn Any>"
                }
                return oneOf.first().renderTypeExpr()
            }

            is IntersectionType -> allOf.map { it.renderTypeExpr() }.joinToString(" : ")
            is NilType -> "None"
            else -> toVrapType().rustTypeName()
        }
    }

    fun UnionType.commonType(): AnyType? {
        return getCommonType(this.oneOf)
    }

    private fun getCommonType(types: List<AnyType>): AnyType? {
        val baseTypes = types.map {
            it.type
        }.filterNotNull()

        if (baseTypes.isEmpty()) return null
        else if (baseTypes.size == 1) {
            return baseTypes.first()
        }

        val first = baseTypes.first()
        var same = true
        baseTypes.forEach {
            if (!it.equals(first))
                same = false
        }

        return if (same) {
            first
        } else {
            getCommonType(baseTypes)
        }
    }

    fun AnyType.moduleName(): String {
        val type = this.toVrapType()
        return when (type) {
            is VrapObjectType -> type.`package`
            is VrapEnumType -> type.`package`
            is VrapScalarType -> "types"
            else -> "unknown"
        }
    }

    fun EObject?.toVrapType(): VrapType {
        val vrapType = if (this != null) vrapTypeProvider.doSwitch(this) else VrapNilType()
        return vrapType.createRustVrapType()
    }

    fun VrapType.createRustVrapType(): VrapType {
        return when (this) {
            is VrapObjectType -> {
                VrapObjectType(`package` = this.`package`.rustModuleFileName(), simpleClassName = this.simpleClassName)
            }

            is VrapEnumType -> {
                VrapEnumType(`package` = this.`package`.rustModuleFileName(), simpleClassName = this.simpleClassName)
            }

            is VrapArrayType -> {
                VrapArrayType(itemType = this.itemType.createRustVrapType())
            }

            else -> this
        }
    }

    fun List<AnyType>.getImportsForModule(moduleName: String): List<String> {
        return this
            .filter { it is ObjectType }
            .map { it as ObjectType }
            .flatMap { it.getDependencies() }
            .getImportsForModelVrapTypes(moduleName)
    }

    private fun ObjectType.getDependencies(): List<VrapType> {
        val subtypeDependencies: List<VrapType> = if (this.isDiscriminated()) {
            this.subTypes.flatMap {
                when (it) {
                    is ObjectType -> it.getDependencies()
                    else -> listOf()
                }
            }
        } else {
            listOf()
        }

        val mapDependency: List<VrapType> = if (this.isMap()) {
            listOf(VrapObjectType("std::collections", "HashMap", externalType = true))
        } else {
            listOf()
        }

        val typeDependencies: List<AnyType> = this.allProperties
            .map { it.type }
            .flatMap { if (it is UnionType) Collections.singletonList(it.commonType()).filterNotNull() else Collections.singletonList(it) }
            .filterNotNull()

        return typeDependencies
            .map { it.toVrapType() }
            .map { it.flattenVrapType() }
            .filterNotNull()
            .filter { it !is VrapScalarType }
            .plus(subtypeDependencies)
            .plus(mapDependency)
    }

    private fun List<VrapType>.getImportsForModelVrapTypes(moduleName: String): List<String> {
        return this
            .map { it.flattenVrapType() }
            .distinct()
            .filter {
                when (it) {
                    is VrapObjectType -> it.`package` != moduleName
                    is VrapEnumType -> it.`package` != moduleName
                    else -> false
                }
            }
            .groupBy {
                when (it) {
                    is VrapObjectType -> Pair(it.`package`, it.externalType)
                    is VrapEnumType -> Pair(it.`package`, false)
                    else -> throw IllegalStateException("this case should have been filtered")
                }
            }
            .toSortedMap { a, b -> a.first.compareTo(b.first) }
            .map { entry ->
                val allImportedClasses = if (entry.value.size == 1)
                    entry.value.first().simpleRustName()
                 else
                    "{${entry.value.map { it.simpleRustName() }.sorted().joinToString(", ")}}"

                val isExternal = entry.key.second
                val prefix = if (isExternal) "" else "crate::"
                "$prefix${entry.key.first}::$allImportedClasses"
            }
    }

    fun ObjectType.rustStructFields(withParentProperties: Boolean, includeDiscriminator: Boolean = false): List<Property> {
        var props: List<Property> = allProperties

        if (!withParentProperties) {
            val parentProps = getSuperProperties().map { it.name }
            props = allProperties.filter { !parentProps.contains(it.name) }
        }

        return props.filter {
            (includeDiscriminator || discriminator() == null || it.name != discriminator()) || (it.name == discriminator() && discriminatorValue == null)
        }
    }

    fun ObjectType.getSuperProperties(): List<Property> {
        return when (this.type) {
            is ObjectType -> (this.type as ObjectType).allProperties
            else -> emptyList<Property>()
        }
    }

    fun AnyType.isDiscriminated(): Boolean {
        if (this !is ObjectType) {
            return false
        }
        if (this.discriminator() != null && this.discriminatorValue.isNullOrEmpty()) {
            val parentType = this.type
            if (parentType is ObjectType && !parentType.discriminatorValue.isNullOrEmpty()) {
                return false
            }
            return true
        }
        return false
    }

    fun ObjectType.isMap(): Boolean {
        if (this.type != null && this.type.getAnnotation("asMap") != null) {
            return true
        }
        if (this.getAnnotation("asMap") != null) {
            return true
        }

        return allProperties.all { it.isPatternProperty() }
    }
}
