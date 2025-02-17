package io.vrap.rmf.codegen.cli

import com.commercetools.rmf.validators.ValidatorSetup
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.vrap.codegen.languages.ramldoc.model.RamldocBaseTypes
import io.vrap.codegen.languages.ramldoc.model.RamldocModelModule
import io.vrap.rmf.codegen.CodeGeneratorConfig
import io.vrap.rmf.codegen.di.OasGeneratorComponent
import io.vrap.rmf.codegen.di.OasGeneratorModule
import io.vrap.rmf.codegen.di.OasProvider
import io.vrap.rmf.raml.model.RamlModelBuilder
import org.eclipse.emf.common.util.URI
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists


@CommandLine.Command(name = "validate", description = ["Allows to verify if a raml spec is valid according to CT guideline"])
class ValidateSubcommand : Callable<Int> {

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["display this help message"])
    var usageHelpRequested = false

    @CommandLine.Parameters(index = "0", description = ["Api file location"])
    lateinit var ramlFileLocation: Path

    @CommandLine.Option(names = ["-r", "--ruleset"], description = ["Ruleset configuration"], required = false)
    var rulesetFile: Path? = null

    @CommandLine.Option(names = ["-t", "--temp"], description = ["Temporary folder"], required = false)
    var tempFile: Path? = null

    @CommandLine.Option(names = ["-w", "--watch"], description = ["Watches the files for changes"], required = false)
    var watch: Boolean = false

    @CommandLine.Option(names = ["-p", "--printer"], required = false)
    var printerType: Printer = Printer.LOGGER

    lateinit var modelBuilder: RamlModelBuilder

    lateinit var ramlDiagnosticPrinter: RamlDiagnosticPrinter

    override fun call(): Int {
        val tmpDir = tempFile ?: Paths.get(".tmp")
        ramlDiagnosticPrinter = Printer.diagnosticPrinter(printerType)
        modelBuilder = setupValidators()
        val res = safeRun { validate(tmpDir)}
        if (watch) {
            val watchDir = ramlFileLocation.toRealPath().toAbsolutePath().parent

            val source = Observable.create<DirectoryChangeEvent> { emitter ->
                run {
                    val watcher = DirectoryWatcher.builder()
                            .path(watchDir)
                            .listener { event ->
                                when (event.eventType()) {
                                    DirectoryChangeEvent.EventType.CREATE,
                                    DirectoryChangeEvent.EventType.MODIFY,
                                    DirectoryChangeEvent.EventType.DELETE -> {
                                        if (event.path().startsWith(tmpDir)) {
                                            return@listener
                                        }
                                        val json = event.path().toString().endsWith("json")
                                        val raml = event.path().toString().endsWith("raml")
                                        val yml = event.path().toString().endsWith("yml")
                                        val yaml = event.path().toString().endsWith("yaml")
                                        if (json || raml || yaml || yml) {
                                            emitter.onNext(event)
                                        }
                                    }
                                    else -> {
                                    }
                                }
                            }
                            .build()
                    watcher.watchAsync()
                }
            }

            source.subscribeOn(Schedulers.io())
                    .throttleLast(1, TimeUnit.SECONDS)
                    .blockingSubscribe(
                            {
                                InternalLogger.debug("Consume ${it.eventType().name.lowercase(Locale.getDefault())}: ${it.path()}")
                                safeRun { validate(tmpDir) }
                            },
                            {
                                InternalLogger.error(it)
                            }
                    )
        }
        return res
    }

    fun validate(tmpDir: Path): Int {
        var fileLocation = ramlFileLocation
        if (ramlFileLocation.toString().endsWith(".raml").not()) {
            val apiProvider = OasProvider(ramlFileLocation)

            val ramlConfig = CodeGeneratorConfig(
                outputFolder = tmpDir,
            )
            val generatorModule = OasGeneratorModule(apiProvider, ramlConfig, RamldocBaseTypes)
            val generatorComponent = OasGeneratorComponent(generatorModule, RamldocModelModule)
            generatorComponent.generateFiles()

            fileLocation = tmpDir.resolve("api.raml")
        }
        val fileURI = URI.createURI(fileLocation.toUri().toString())
        val modelResult = modelBuilder.buildApi(fileURI)
//        if (tmpDir.exists()) {
//            tmpDir.toFile().deleteRecursively()
//        }
        return ramlDiagnosticPrinter.print(fileURI, modelResult);
    }

    private fun setupValidators(): RamlModelBuilder {
        val ruleset = rulesetFile?.toFile()?.inputStream() ?: ValidateSubcommand::class.java.getResourceAsStream("/ruleset.xml")
        return RamlModelBuilder(ValidatorSetup.setup(ruleset))
    }
}


