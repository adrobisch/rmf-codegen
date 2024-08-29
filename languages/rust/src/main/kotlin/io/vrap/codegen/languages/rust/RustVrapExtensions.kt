package io.vrap.codegen.languages.rust

import io.vrap.rmf.codegen.types.*

fun VrapType.rustTypeName(): String {
    return when (this) {
        is VrapAnyType -> this.baseType
        is VrapScalarType -> this.scalarType
        is VrapEnumType -> this.simpleClassName.exportName()
        is VrapObjectType -> this.simpleClassName.exportName()
        is VrapArrayType -> "Vec\\<${this.itemType.rustTypeName()}\\>"
        is VrapNilType -> "todo!()"
    }
}

fun VrapType.qualifiedName(baseCrate: String): String? {
    return when (this) {
        is VrapEnumType -> "$baseCrate::${this.`package`}::${this.rustTypeName()}"
        is VrapObjectType -> "$baseCrate::${this.`package`}::${this.rustTypeName()}"
        else -> this.rustTypeName()
    }
}

fun VrapType.flattenVrapType(): VrapType {
    return when (this) {
        is VrapArrayType -> {
            this.itemType.flattenVrapType()
        }
        else -> this
    }
}
