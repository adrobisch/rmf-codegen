package io.vrap.codegen.languages.rust.client

import io.vrap.rmf.codegen.di.Module
import io.vrap.rmf.codegen.di.RamlGeneratorModule
import io.vrap.rmf.codegen.rendering.*

object RustClientModule : Module {

    override fun configure(generatorModule: RamlGeneratorModule) = setOf (
        ResourceGenerator(
            setOf(
                RequestBuilder(
                    generatorModule.clientConstants(),
                    generatorModule.provideRamlModel(),
                    generatorModule.vrapTypeProvider(),
                    generatorModule.providePackageName()
                )
            ),
            generatorModule.allResources()
        ),
        FileGenerator(
            setOf(
                RustClientFileProducer(
                    generatorModule.clientConstants(),
                    generatorModule.provideRamlModel(),
                    generatorModule.providePackageName()
                )
            )
        )
    )

    private fun RamlGeneratorModule.clientConstants() =
        ClientConstants(
            this.provideSharedPackageName(),
            this.provideClientPackageName(),
            this.providePackageName()
        )
}
