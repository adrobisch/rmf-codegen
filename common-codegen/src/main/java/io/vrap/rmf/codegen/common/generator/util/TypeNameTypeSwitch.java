package io.vrap.rmf.codegen.common.generator.util;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.vrap.rmf.raml.model.types.*;
import io.vrap.rmf.raml.model.types.util.TypesSwitch;
import org.joda.time.LocalTime;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public class TypeNameTypeSwitch extends TypesSwitch<TypeName> {
    private final PackageSwitch packageSwitch;
    private final Converter<String, String> classNameMapper = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.UPPER_CAMEL);

    public TypeNameTypeSwitch(PackageSwitch packageSwitch) {
        this.packageSwitch = packageSwitch;
    }

    @Override
    public TypeName caseAnyType(AnyType object) {
        return TypeName.get(Object.class);
    }

    @Override
    public TypeName caseNumberType(NumberType object) {
        return TypeName.get(Integer.class);
    }

    @Override
    public TypeName caseIntegerType(IntegerType object) {
        return TypeName.get(Integer.class);
    }

    @Override
    public TypeName caseBooleanType(BooleanType object) {
        return TypeName.get(Boolean.class);
    }

    @Override
    public TypeName caseDateTimeType(DateTimeType object) {
        return TypeName.get(ZonedDateTime.class);
    }

    @Override
    public TypeName caseTimeOnlyType(TimeOnlyType object) {
        return TypeName.get(LocalTime.class);
    }

    @Override
    public TypeName caseDateOnlyType(DateOnlyType object) {
        return TypeName.get(LocalDate.class);
    }

    @Override
    public TypeName caseArrayType(ArrayType arrayType) {
        return ParameterizedTypeName.get(ClassName.get(List.class), doSwitch(arrayType.getItems()));
    }


    @Override
    public TypeName caseObjectType(ObjectType objectType) {
        return ClassName.get(packageSwitch.doSwitch(objectType), classNameMapper.convert(objectType.getName()));
    }

    @Override
    public TypeName caseStringType(StringType stringType) {
        if (stringType.getName().equalsIgnoreCase("string")
        ||stringType.getEnum() == null
        ||stringType.getEnum().isEmpty()
        ) {
            return TypeName.get(String.class);
        }
        //This case happens for enumerations
        return ClassName.get(packageSwitch.doSwitch(stringType), classNameMapper.convert(stringType.getName()));
    }





}