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
    current_token: Option<TokenResponse>,
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


    pub async fn request<U: IntoUrl>(&mut self, method: reqwest::Method, path: U) -> Result<RequestBuilder, SdkError> {
        let token: String = match &self.current_token {
            Some(token) => token.access_token.to_string(),
            None => {
                let response = self.client.post(format!("{}/oauth/token", self.auth_url))
                    .basic_auth(&self.credentials.client_id, Some(&self.credentials.client_secret))
                    .form(&[("grant_type", "client_credentials")])
                    .send()
                    .await?
                    .json::<TokenResponse>()
                    .await?;
                self.current_token = Some(response.clone());
                response.access_token
            }
        };
        Ok(self.client.request(method, format!("{}{}", self.api_url, path.as_str())).bearer_auth(token))

    }
}

#[derive(serde::Deserialize, Clone)]
struct TokenResponse {
    access_token: String,
    token_type: String,
    expires_in: u64,
    scope: String,
}