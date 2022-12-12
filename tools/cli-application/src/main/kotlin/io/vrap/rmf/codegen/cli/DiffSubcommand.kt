package io.vrap.rmf.codegen.cli

import com.fasterxml.jackson.databind.ObjectMapper
import io.vrap.codegen.languages.csharp.extensions.pluralize
import io.vrap.rmf.codegen.cli.diff.*
import io.vrap.rmf.codegen.firstUpperCase
import io.vrap.rmf.raml.model.RamlModelBuilder
import io.vrap.rmf.raml.model.modules.Api
import org.eclipse.emf.common.util.URI
import picocli.CommandLine
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.stream.Collectors

@CommandLine.Command(name = "diff", description = ["Generates a diff between two specifications"])
class DiffSubcommand : Callable<Int> {

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["display this help message"])
    var usageHelpRequested = false

    @CommandLine.Parameters(index = "0", description = ["Original api file location"])
    lateinit var originalFileLocation: Path

    @CommandLine.Parameters(index = "1", description = ["Changed api file location"])
    lateinit var changedFileLocation: Path

    @CommandLine.Option(names = ["-f", "--format"], description = ["Specifies the output format","Valid values: ${OutputFormat.VALID_VALUES}"])
    var outputFormat: OutputFormat = OutputFormat.CLI

    @CommandLine.Option(names = ["-o", "--outputTarget"], description = ["Specifies the file to write to"])
    var outputTarget: Path? = null

    @CommandLine.Option(names = ["-d", "--diffs"], description = ["Diff configuration"])
    var diffConfigurationFile: Path? = null

    @CommandLine.Option(names = ["-s", "--severity"], description = ["Check severity", "Valid values: ${CheckSeverity.VALID_VALUES}"])
    var checkSeverity: CheckSeverity = CheckSeverity.FATAL

    override fun call(): Int {
        return diff()
    }

    private fun diff(): Int {
        val originalApi = readApi(originalFileLocation.toRealPath().toAbsolutePath())
        val changedApi = readApi(changedFileLocation.toRealPath().toAbsolutePath())

        val config = diffConfigurationFile?.toFile()?.inputStream() ?: ValidateSubcommand::class.java.getResourceAsStream("/diff.xml")

        val differ = RamlDiff.Builder()
            .original(originalApi)
            .changed(changedApi)
            .plus(DiffSetup.setup(config))
            .build()
        val diffResult = differ.diff()

        if (diffResult.isEmpty()) {
            return 0
        }

        val output = diagnosticPrinter(outputFormat).print(diffResult)

        outputTarget?.let {
            InternalLogger.info("Writing to ${it.toAbsolutePath().normalize()}")
            Files.write(it.toAbsolutePath().normalize(), output.toByteArray(StandardCharsets.UTF_8))
        } ?: run {
            println(output)
        }
        return diffResult.any { diff -> diff.severity >= checkSeverity }.let { b -> if(b) 1 else 0 }
    }

    companion object {
        fun diagnosticPrinter(printer: OutputFormat): FormatPrinter {
            return when (printer) {
                OutputFormat.CLI -> CliFormatPrinter()
                OutputFormat.MARKDOWN -> MarkdownFormatPrinter()
                OutputFormat.JAVA_MARKDOWN -> JavaMarkdownFormatPrinter()
                OutputFormat.JSON -> JsonFormatPrinter()
            }
        }
    }

    interface FormatPrinter {
        fun print(diffResult: List<Diff<Any>>): String
    }

    class CliFormatPrinter: FormatPrinter {
        override fun print(diffResult: List<Diff<Any>>): String {
            return diffResult.joinToString("\n") { "${it.message} (${it.source})" }
        }
    }

    class MarkdownFormatPrinter: FormatPrinter {
        override fun print(diffResult: List<Diff<Any>>): String {

            val map = diffResult.groupBy { it.scope }.map { it.key to it.value.groupBy { it.diffType } }.toMap()

            return map.entries.joinToString("\n\n") { scope -> """
                |${scope.value.entries.joinToString("\n\n") { type -> """
                |<details>
                |<summary>${type.key.type.firstUpperCase()} ${scope.key.scope.firstUpperCase()}(s)</summary>
                |
                |${type.value.joinToString("\n") { "- ${it.severity.asSign()}${it.message} (${it.source?.location}:${it.source?.position?.line}:${it.source?.position?.charPositionInLine})" }}
                |</details>
                |
                """.trimMargin() }}
            """.trimMargin() }
        }

        fun CheckSeverity.asSign(): String {
            return when(this) {
                CheckSeverity.FATAL -> ":red_circle: "
                CheckSeverity.ERROR -> ":warning: "
                CheckSeverity.WARN -> ":warning: "
                else -> ""

            }
        }
    }

    class JsonFormatPrinter: FormatPrinter {
        override fun print(diffResult: List<Diff<Any>>): String {
            val mapper = ObjectMapper()
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(diffResult)
        }
    }

    public class JavaMarkdownFormatPrinter: FormatPrinter {

        fun replaceMessage(diff: Diff<Any>): Diff<Any> {
            val values: Array<String> = diff.value.toString().split(" /").toTypedArray()
            return when (diff.scope) {
                Scope.METHOD -> Diff(diff.diffType, diff.scope, diff.value, "${diff.diffType.toString().lowercase()} ${diff.scope.toString().lowercase()} `apiRoot.withProjectKey(\"\").${values[1]}().${values[0]}()`", diff.eObject, diff.severity, diff.diffEObject, diff.source)
                else -> diff
            }
        }
        override fun print(diffResult: List<Diff<Any>>): String {

            val map = diffResult.map { replaceMessage(it) }.groupBy { it.scope }.map { it.key to it.value.groupBy { it.diffType } }.toMap()


            return map.entries.joinToString("\n\n") { scope -> """
                |${scope.value.entries.joinToString("\n\n") { type -> """
                |<details>
                |<summary>${type.key.type.firstUpperCase()} ${scope.key.scope.firstUpperCase()}(s)</summary>
                |
                |${type.value.joinToString("\n") { "- ${it.severity.asSign()}${it.message}" }}
                |</details>
                |
                """.trimMargin() }}
            """.trimMargin() }
        }

        fun CheckSeverity.asSign(): String {
            return when(this) {
                CheckSeverity.FATAL -> ":red_circle: "
                CheckSeverity.ERROR -> ":warning: "
                CheckSeverity.WARN -> ":warning: "
                else -> ""

            }
        }
    }

    private fun readApi(fileLocation: Path): Api {
        val fileURI = URI.createURI(fileLocation.toUri().toString())
        InternalLogger.info("Reading ${fileURI.toFileString()} ...")
        val modelResult = RamlModelBuilder().buildApi(fileURI)
        val validationResults = modelResult.validationResults
        if (validationResults.isNotEmpty()) {
            val res = validationResults.stream().map { "$it" }.collect( Collectors.joining( "\n" ) );
            InternalLogger.error("Error(s) found validating ${fileURI.toFileString()}:\n$res")
            return modelResult.rootObject
        }
        InternalLogger.info("\tdone")

        return modelResult.rootObject
    }
}
