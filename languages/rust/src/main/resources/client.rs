// Generated file, please do not change!!!
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
            None => todo!("implement auth flow")
        }
    }
}