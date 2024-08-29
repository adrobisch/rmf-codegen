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
        val clientMethodFiles = api.allContainedResources.map { resource -> """pub mod ${resource.rustClientFileName()};""" }
            .joinToString("\n")

        return TemplateFile(
            relativePath = "client/src/lib.rs",
            content = """$rustGeneratedComment
                |$clientMethodFiles
                |pub mod errors;
                |pub mod client;
            """.trimMargin().keepIndentation())
    }

    fun produceClientFile(): TemplateFile {
        return TemplateFile(
            relativePath = "client/src/client.rs",
            content = """$rustGeneratedComment
use reqwest::IntoUrl;
use reqwest::RequestBuilder;

use crate::errors::SdkError;

struct ClientCredentials {
    client_id: String,
    client_secret: String
}

pub struct ClientContext {
    auth_url: String,
    api_url: String,
    current_token: Option<String>,
    client: reqwest::Client,
    credentials: ClientCredentials
}

impl ClientContext {
    pub fn default(auth_url: String, api_url: String, client_id: String, client_secret: String) -> Self {
        ClientContext {
            credentials: ClientCredentials {
                client_id,
                client_secret
            },
            auth_url,
            api_url,
            current_token: None,
            client: reqwest::Client::default(),
        }
    }

    pub fn from_client(auth_url: String, api_url: String, client_id: String, client_secret: String, client: reqwest::ClientBuilder) -> Result<Self, SdkError> {
        Ok(ClientContext {
            credentials: ClientCredentials {
                client_id,
                client_secret
            },
            auth_url,
            api_url,
            current_token: None,
            client: client.build()?,
        })
    }

    pub async fn request<U: IntoUrl>(&self, method: reqwest::Method, path: U) -> Result<RequestBuilder, SdkError> {
        match &self.current_token {
            Some(token) => Ok(self.client.request(method, format!("{}{}", self.api_url, path.as_str())).bearer_auth(token)),
            None => todo!()
        }
    }
}
""".trimMargin()
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
