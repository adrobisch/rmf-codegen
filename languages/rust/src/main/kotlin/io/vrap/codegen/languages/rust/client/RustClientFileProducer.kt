package io.vrap.codegen.languages.rust.client

import io.vrap.codegen.languages.rust.*
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendering.FileProducer
import io.vrap.rmf.codegen.rendering.utils.keepIndentation
import io.vrap.rmf.raml.model.modules.Api

class RustClientFileProducer constructor(
    val clientConstants: ClientConstants,
    val api: Api,
    @BasePackageName val basePackageName: String
) : FileProducer {

    override fun produceFiles(): List<TemplateFile> {
        return listOf(
            produceClientFile(),
            produceErrorsFile(),
        )
    }

    fun produceClientFile(): TemplateFile {
        return TemplateFile(
            relativePath = "src/client/client.rs",
            content = """|
                |$rustGeneratedComment
            """.trimMargin()
        )
    }

    fun produceErrorsFile(): TemplateFile {
        return TemplateFile(
            relativePath = "src/client/errors.rs",
            content = """|
                |$rustGeneratedComment
            """.trimMargin().keepIndentation()
        )
    }
}
