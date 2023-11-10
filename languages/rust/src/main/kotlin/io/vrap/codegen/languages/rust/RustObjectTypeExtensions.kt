package io.vrap.codegen.languages.rust

import io.vrap.codegen.languages.extensions.ExtensionsBase
import io.vrap.codegen.languages.extensions.isPatternProperty
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.types.*
import org.eclipse.emf.ecore.EObject
import java.util.*
import kotlin.collections.HashMap

interface RustObjectTypeExtensions : ExtensionsBase {
    fun isRecursive(findObjectType: ObjectType): Boolean {
        val typesToCheck = Stack<AnyType>()
        val visited = HashMap<AnyType, Boolean>()
        visited[findObjectType] = true

        findObjectType.allProperties.forEach { typesToCheck.push(it.type) }

        while (!typesToCheck.isEmpty()) {
            val next = typesToCheck.pop() ?: continue

            if (visited[next] == true) {
                continue
            } else {
                visited[next] = true
            }

            if (next.toVrapType() == findObjectType.toVrapType()) {
                return true
            }

            when (next) {
                is ObjectType -> next.allProperties.forEach { typesToCheck.push(it.type) }
            }
        }
        return false
    }

    fun AnyType.renderTypeExpr(): String {
        return when (this) {
            is ObjectType -> {
                val typeName = toVrapType().rustTypeName()

                if(isRecursive(this)) {
                    return "Box\\<${typeName}\\>"
                } else {
                    return typeName
                }
            }
            is UnionType -> {
                // Go has no concept of unions so we look for the shared common
                // type. If none found we should use interface{}
                if (oneOf.size > 1) {
                    val common = commonType(oneOf)
                    if (common != null) {
                        return common.renderTypeExpr()
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

    private fun commonType(types: List<AnyType>): AnyType? {
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
            commonType(baseTypes)
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

    fun List<AnyType>.getEnumVrapTypes(): List<VrapType> {
        return this
            .filterIsInstance<ObjectType>()
            .flatMap { it.allProperties }
            .map { it.type.toVrapType() }
            .map {
                when (it) {
                    is VrapEnumType -> it
                    is VrapArrayType ->
                        when (it.itemType) {
                            is VrapEnumType -> it
                            else -> null
                        }

                    else -> null
                }
            }
            .filterNotNull()
    }

    fun List<AnyType>.getImportsForModule(moduleName: String): List<String> {
        return this
            .filter { it is ObjectType }
            .map { it as ObjectType }
            .flatMap { it.getDependencies() }
            .getImportsForModelVrapTypes(moduleName)
    }

    private fun ObjectType.getDependencies(): List<VrapType> {
        var dependentTypes = this.allProperties
            .map { it.type }
            .plus(subTypes.plus(subTypes.flatMap { it.subTypes }).distinctBy { it.name })
            .plus(type)
            .flatMap { if (it is UnionType) Collections.singletonList(commonType(it.oneOf)).filterNotNull() else Collections.singletonList(it) }
            .filterNotNull()

        return dependentTypes
            .map { it.toVrapType() }
            .map { it.flattenVrapType() }
            .filterNotNull()
            .filter { it !is VrapScalarType }
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
                    is VrapObjectType -> it.`package`
                    is VrapEnumType -> it.`package`
                    else -> throw IllegalStateException("this case should have been filtered")
                }
            }
            .toSortedMap()
            .map {
                val allImportedClasses = it.value.map { it.simpleRustName() }.sorted().joinToString(", ")
                "crate::${it.key}::{$allImportedClasses}"
            }
    }

    fun ObjectType.rustStructFields(all: Boolean): List<Property> {
        var props: List<Property> = allProperties

        if (!all) {
            val parentProps = getSuperProperties().map { it.name }
            props = allProperties.filter { !parentProps.contains(it.name) }
        }
        return props.filter {
            (
                    (discriminator() == null || it.name != discriminator()) ||
                            (it.name == discriminator() && discriminatorValue == null)
                    )
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
