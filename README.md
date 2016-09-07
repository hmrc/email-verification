# email-verification

[![Build Status](https://travis-ci.org/hmrc/email-verification.svg)](https://travis-ci.org/hmrc/email-verification) [ ![Download](https://api.bintray.com/packages/hmrc/releases/email-verification/images/download.svg) ](https://bintray.com/hmrc/releases/email-verification/_latestVersion)

# About

email-verification is a service to support a verification flow that starts by creating a verification for an email address and sending out an email with verification link.

Users can verify the email address by clicking on the link in the email.

Endpoints are provided to trigger a verification email, verify email by clicking the link and retrieve a verification status for a given email address.

# How to build

Preconditions:
- ```smserver``` needs to be running for it:test
- ```mongod``` needs to be running for it:test

```sbt test it:test```

# API

    | Path                             | Supported Methods | Description                                               |
    |----------------------------------|-------------------|-----------------------------------------------------------|
    | /verification-requests           | POST              | Create a new verification request                         |
    | /verified-email-addresses        | POST              | Create a new verified email address                       |
    | /verified-email-addresses/:email | GET               | Check if email address is verified                        |


## POST /verification-requests

Create a new verification request

**Request body**

```json
{
  "email": "gary@example.com",
  "templateId": "anExistingTemplateInEmailServiceId",
  "templateParameters": {
    "name": "Gary Doe"
  },
  "linkExpiryDuration" : "P1D",
  "continueUrl" : "http://some-continue.url"
}
```

The template identified by ```templateId``` must contain a parameter named ```verificationLink```
```linkExpiryDuration``` is the validity in [ISO8601](https://en.wikipedia.org/wiki/ISO_8601#Durations) format

### Success Response

    | Status    |  Description                      |
    |-----------|-----------------------------------|
    | 201       | Verification created successfully |

### Failure Responses

    | Status    |  Description                                  |  Code                    |
    |-----------|-----------------------------------------------|--------------------------|
    | 400       | Invalid request                               | VALIDATION_ERROR         |
    | 409       | Email has already been verified               | EMAIL_VERIFIED_ALREADY   |
    | 400       | Bad request to email, like template not found | BAD_EMAIL_REQUEST        |
    | 500       | Unexpected error                              | UNEXPECTED_ERROR         |
    | 502       | Upstream service error                        | UPSTREAM_ERROR           |
    

## POST /verified-email-addresses

Create a new verified email address

**Request body**

```json
{
  "token": "qwerty1234567890"
}
```
### Success Responses

    | Status    |  Description                      |
    |-----------|-----------------------------------|
    | 201       | Verification created successfully |
    | 204       | Verification already existing     |

### Failure Responses

    | Status    |  Description                      |  Code                      |
    |-----------|-----------------------------------|----------------------------|
    | 400       | Token not found or expired        | TOKEN_NOT_FOUND_OR_EXPIRED |
    | 500       | Unexpected error                  | UNEXPECTED_ERROR           |
    | 502       | Upstream service error            | UPSTREAM_ERROR             |


## GET /verified-email-addresses/:email

Check if email address is verified

### Success Response

    | Status    |  Description                      |
    |-----------|-----------------------------------|
    | 200       | Email is verified                 |

### Failure Responses

    | Status    |  Description                      |  Code                            |
    |-----------|-----------------------------------|----------------------------------|
    | 404       | Email not found / not verified    | EMAIL_NOT_FOUND_OR_NOT_VERIFIED  |
    | 500       | Unexpected error                  | UNEXPECTED_ERROR                 |
    | 502       | Upstream service error            | UPSTREAM_ERROR                   |

**Response body**

```json
{
  "email": "some.email.address@yahoo.co.uk"
}
```

## Error response payload structure
Error responses are mapped to the following json structure returned as the response body
with the appropriate http error status code. E.g.:

```json
{
  "code": "TOKEN_NOT_FOUND_OR_EXPIRED",
  "message":"Token not found or expired."
}
```

or with details (optional):

```json
{
  "code": "VALIDATION_ERROR",
  "message":"Payload validation failed",
  "details":{
    "obj.email": "error.path.missing"
  }
}
```

## Generic errors

**Payload validation errors are returning with 400 http status**

```json
{
  "code": "VALIDATION_ERROR",
  "message":"Payload validation failed",
  "details":{
    "obj.email": "error.path.missing"
  }
}
```

**Not found errors are returning with 404 http status and a response body:**

```json
{
  "code":"NOT_FOUND",
  "message":"URI not found",
  "details": {
     "requestedUrl":"/email-verification/non-existent-url"
  }
}
```

**Unexpected errors are returning with 500 http status and a response body:**

```json
{
  "code":"UNEXPECTED_ERROR",
  "message":"An unexpected error occured"
}
```

**upstream errors are returning with 502 http status and a response body:**

```json
{
  "code":"UPSTREAM_ERROR",
  "message":"POST of 'http://localhost:11111/send-templated-email' returned 500. Response body: 'some-5xx-message'"
}
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
