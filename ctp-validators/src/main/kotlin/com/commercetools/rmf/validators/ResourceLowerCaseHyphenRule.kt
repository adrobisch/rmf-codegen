package com.commercetools.rmf.validators

import com.damnhandy.uri.template.Literal
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.util.StringCaseFormat
import org.eclipse.emf.common.util.Diagnostic
import java.util.*

@RulesSet
class ResourceLowerCaseHyphenRule(severity: RuleSeverity, options: List<RuleOption>? = null) : ResourcesRule(severity, options) {

    private val exclude: List<String> =
        (options?.filter { ruleOption -> ruleOption.type.lowercase(Locale.getDefault()) == RuleOptionType.EXCLUDE.toString() }?.map { ruleOption -> ruleOption.value }?.plus("") ?: defaultExcludes)

    override fun caseResource(resource: Resource): List<Diagnostic> {
        val validationResults: MutableList<Diagnostic> = ArrayList()

        if (exclude.contains(resource.fullUri.template).not() && resource.relativeUri.components.filterIsInstance(Literal::class.java)
                .any { literal -> literal.value != StringCaseFormat.LOWER_HYPHEN_CASE.apply(literal.value) && exclude.contains(literal.value).not() }
        ) {
            validationResults.add(create(resource, "Resource \"{0}\" must be lower case hyphen separated", resource.fullUri.template))
        }
        return validationResults
    }

    companion object : ValidatorFactory<ResourceLowerCaseHyphenRule> {
        private val defaultExcludes by lazy { listOf("") }

        @JvmStatic
        override fun create(options: List<RuleOption>?): ResourceLowerCaseHyphenRule {
            return ResourceLowerCaseHyphenRule(RuleSeverity.ERROR, options)
        }

        @JvmStatic
        override fun create(severity: RuleSeverity, options: List<RuleOption>?): ResourceLowerCaseHyphenRule {
            return ResourceLowerCaseHyphenRule(severity, options)
        }
    }
}
