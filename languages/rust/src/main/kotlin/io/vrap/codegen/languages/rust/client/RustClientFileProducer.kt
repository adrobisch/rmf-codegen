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
            produceLibFile(),
            produceClientFile(),
            produceErrorsFile(),
        )
    }

    fun produceLibFile(): TemplateFile {
        val clientMethodFiles = api.allContainedResources.map { resource -> """mod ${resource.rustClientFileName()};""" }
            .joinToString("\n")

        return TemplateFile(
            relativePath = "client/src/lib.rs",
            content = """$rustGeneratedComment
                |$clientMethodFiles
                |mod errors;
            """.trimMargin().keepIndentation())
    }

    fun produceClientFile(): TemplateFile {
        return TemplateFile(
            relativePath = "client/src/client.rs",
            content = """$rustGeneratedComment\n""".trimMargin()
        )
    }

    fun produceErrorsFile(): TemplateFile {
        return TemplateFile(
            relativePath = "client/src/errors.rs",
            content = """|$rustGeneratedComment
use thiserror::Error;

#[derive(Error, Debug)]
pub enum SdkError {
    #[error("http error")]
    HttpError(#[from] reqwest::Error)
}
""".trimMargin().keepIndentation())
    }
}
