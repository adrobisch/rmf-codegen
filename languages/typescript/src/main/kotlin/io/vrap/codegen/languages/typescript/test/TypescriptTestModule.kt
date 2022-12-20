
package io.vrap.codegen.languages.typescript.test

import io.vrap.codegen.languages.extensions.deprecated
import io.vrap.rmf.codegen.di.RamlGeneratorModule
import io.vrap.rmf.codegen.di.Module
import io.vrap.rmf.codegen.rendering.*

object TypescriptTestModule: Module {
    override fun configure(generatorModule: RamlGeneratorModule) = setOf<CodeGenerator> (
        ResourceGenerator(
                setOf(
                        TypescriptRequestTestRenderer(generatorModule.vrapTypeProvider())
                ), generatorModule.allResources().filterNot { it.deprecated() }
        )
    )
}
