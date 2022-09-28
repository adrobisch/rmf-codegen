package com.commercetools.rmf.validators

import com.ctc.wstx.stax.WstxInputFactory
import com.ctc.wstx.stax.WstxOutputFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.vrap.rmf.raml.validation.RamlValidator
import java.io.File
import java.io.InputStream
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

class ValidatorSetup {
    companion object {
        @JvmStatic
        fun setup(config: File): List<RamlValidator> {
            return setup(config.inputStream())
        }

        fun setup(config: InputStream): List<RamlValidator> {
            val mapper = XmlMapper.builder(XmlFactory(WstxInputFactory(), WstxOutputFactory())).defaultUseWrapper(false)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.WRAP_ROOT_VALUE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .build()
//                .registerKotlinModule()

            val ruleSet = mapper.readValue(config, RuleSet::class.java)

            val validators = ruleSet.rules.map { rule -> when(rule.severity != null) {
                    true -> Class.forName(rule.name).getConstructor(RuleSeverity::class.java, List::class.java).newInstance(rule.severity, rule.options)
                    else -> Class.forName(rule.name).getConstructor(RuleSeverity::class.java, List::class.java).newInstance(RuleSeverity.ERROR, rule.options)
                }
            }

            return listOf(
                ResolvedResourcesValidator(validators.filterIsInstance( ResolvedResourcesRule::class.java )),
                ResourcesValidator(validators.filterIsInstance( ResourcesRule::class.java )),
                TypesValidator(validators.filterIsInstance( TypesRule::class.java )),
                ModulesValidator(validators.filterIsInstance( ModulesRule::class.java ))
            )
        }
    }
}
