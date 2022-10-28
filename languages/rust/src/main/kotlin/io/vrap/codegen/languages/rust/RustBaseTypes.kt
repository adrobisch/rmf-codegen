package io.vrap.codegen.languages.rust

import io.vrap.rmf.codegen.types.LanguageBaseTypes
import io.vrap.rmf.codegen.types.VrapScalarType

object RustBaseTypes : LanguageBaseTypes(
    anyType = nativeRustType("&dyn Any"),
    objectType = nativeRustType("&dyn Any"),
    integerType = nativeRustType("u32"),
    longType = nativeRustType("u64"),
    doubleType = nativeRustType("f64"),
    stringType = nativeRustType("String"),
    booleanType = nativeRustType("bool"),
    dateTimeType = nativeRustType("DateTime\\<Utc\\>"),
    dateOnlyType = nativeRustType("DateTime\\<Utc\\>"),
    timeOnlyType = nativeRustType("DateTime\\<Utc\\>"),
    file = nativeRustType("std::fs::File")
)

fun nativeRustType(typeName: String): VrapScalarType = VrapScalarType(typeName)
