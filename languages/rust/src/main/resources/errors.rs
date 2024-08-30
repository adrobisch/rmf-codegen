// Generated file, please do not change!!!
use thiserror::Error;

#[derive(Error, Debug)]
pub enum SdkError {
    #[error("http error")]
    HttpError(#[from] reqwest::Error)
}
