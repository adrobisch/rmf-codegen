package io.vrap.codegen.languages.javalang.client.builder.module

import io.vrap.codegen.languages.extensions.deprecated
import io.vrap.codegen.languages.javalang.client.builder.producers.JavaApiRootFileProducer
import io.vrap.codegen.languages.javalang.client.builder.requests.JavaHttpRequestRenderer
import io.vrap.codegen.languages.javalang.client.builder.requests.JavaRequestBuilderResourceRenderer
import io.vrap.rmf.codegen.di.RamlGeneratorModule
import io.vrap.rmf.codegen.di.Module
import io.vrap.rmf.codegen.rendering.*


object JavaClientBuilderModule: Module {
    override fun configure(generatorModule: RamlGeneratorModule) = setOf(
            FileGenerator(setOf(
                    JavaApiRootFileProducer(generatorModule.provideClientPackageName(), generatorModule.provideRamlModel())
            )),
            ResourceGenerator(setOf(
                    JavaRequestBuilderResourceRenderer(generatorModule.vrapTypeProvider())
            ), generatorModule.allResources().filterNot { it.deprecated() }),
            MethodGenerator(setOf(
                    JavaHttpRequestRenderer(generatorModule.vrapTypeProvider())
            ), generatorModule.allResourceMethods().filterNot { it.deprecated() })
    )
}
