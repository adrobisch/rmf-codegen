package io.vrap.codegen.languages

import io.vrap.codegen.languages.rust.GoBaseTypes
import io.vrap.codegen.languages.rust.client.RustClientModule
import io.vrap.codegen.languages.rust.model.RustModelModule
import io.vrap.rmf.codegen.CodeGeneratorConfig
import io.vrap.rmf.codegen.di.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

class TestGoCodeGenerator {

    companion object {
        private val userProvidedPath = System.getenv("TEST_RAML_FILE")
        private val apiPath: Path = Paths.get(if (userProvidedPath == null) "../../api-spec/api.raml" else userProvidedPath)
        val apiProvider = RamlApiProvider(apiPath)
        val generatorConfig = CodeGeneratorConfig(basePackageName = "")
    }

    @Test
    fun generateGoModels() {
        val generatorModule = RamlGeneratorModule(apiProvider, generatorConfig, GoBaseTypes)
        val generatorComponent = RamlGeneratorComponent(generatorModule, RustModelModule, RustClientModule)
        generatorComponent.generateFiles()
    }

    private fun cleanGenTestFolder() {
        cleanFolder("build/gensrc")
    }

    private fun cleanFolder(path: String) {
        Paths.get(path).toFile().deleteRecursively()
    }
}
