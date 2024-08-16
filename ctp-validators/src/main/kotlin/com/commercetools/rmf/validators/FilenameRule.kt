package com.commercetools.rmf.validators

import io.vrap.rmf.nodes.antlr.NodeToken
import io.vrap.rmf.raml.model.modules.TypeContainer
import io.vrap.rmf.raml.model.types.AnyType
import io.vrap.rmf.raml.persistence.antlr.RAMLParser
import io.vrap.rmf.raml.persistence.constructor.RamlParserAdapter
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import org.eclipse.emf.common.util.Diagnostic
import java.util.*
import kotlin.collections.ArrayList

@ValidatorSet
class FilenameRule(severity: RuleSeverity, options: List<RuleOption>? = null) : ModulesRule(severity, options) {
    private val exclude: List<String> =
        (options?.filter { ruleOption -> ruleOption.type.lowercase(Locale.getDefault()) == RuleOptionType.EXCLUDE.toString() }?.map { ruleOption -> ruleOption.value }?.plus("") ?: defaultExcludes)

    override fun caseTypeContainer(container: TypeContainer): List<Diagnostic> {
        val validationResults: MutableList<Diagnostic> = ArrayList()

        if (container.types != null) {
            container.types
                .filterNot { exclude.contains(it.name) }
                .forEach {
                    val r = it?.eAdapters()?.filterIsInstance(RamlParserAdapter::class.java)?.map {
                            adapter -> checkIncludedTypeFileName(it, adapter.parserRuleContext)
                    } ?: emptyList()
                    if (r.contains(true).not()) {
                        validationResults.add(create(it, "Type \"{0}\" must have the same file name as type itself", it.name))
                    }
                }
        }

        return validationResults
    }

    private fun checkIncludedTypeFileName(type: AnyType, context: ParserRuleContext): Boolean {
        if (context is RAMLParser.TypeDeclarationMapContext) {
            val id = context.name
            val idIndex = context.children.indexOf(context.name)
            val nextNode = context.children.getOrNull(idIndex + 1)
            if (nextNode is TerminalNode && (nextNode.symbol as NodeToken).location != (id.start as NodeToken).location) {
                return (nextNode.symbol as NodeToken).location.contains(type.name + ".raml")
            }
        }

        return true
    }

    companion object : ValidatorFactory<FilenameRule> {
        private val defaultExcludes by lazy { listOf("") }

        @JvmStatic
        override fun create(options: List<RuleOption>?): FilenameRule {
            return FilenameRule(RuleSeverity.ERROR, options)
        }

        @JvmStatic
        override fun create(severity: RuleSeverity, options: List<RuleOption>?): FilenameRule {
            return FilenameRule(severity, options)
        }
    }
}
