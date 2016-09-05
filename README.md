# email-verification

[![Build Status](https://travis-ci.org/hmrc/email-verification.svg)](https://travis-ci.org/hmrc/email-verification) [ ![Download](https://api.bintray.com/packages/hmrc/releases/email-verification/images/download.svg) ](https://bintray.com/hmrc/releases/email-verification/_latestVersion)

# About

Preconditions:
- ```smserver``` needs to be running for it:test
- ```mongod``` needs to be running for it:test

```sbt test it:test```

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

### Response with

    | Status    |  Description                      |
    |-----------|-----------------------------------|
    | 201       | Verification created successfully |
    | 400       | Invalid request                   |
    | 409       | Email has already been verified   |
    | 400       | Email template not found          |
    | 500       | Unexpected error                  |
    

## POST /verified-email-addresses

Create a new verified email address

**Request body**

```json
{
  "token": "qwerty1234567890"
}
```
### Response with

    | Status    |  Description                      |
    |-----------|-----------------------------------|
    | 201       | Verification created successfully |
    | 204       | Verification already existing     |
    | 400       | Token not found or expired        |
    | 500       | Unexpected error                  |


## GET /verified-email-addresses/:email

Check if email address is verified

### Response with

    | Status    |  Description                      |
    |-----------|-----------------------------------|
    | 200       | Email is verified                 |
    | 404       | Email not found / not verified    |
    | 500       | Unexpected error                  |

**Response body**

```json
{
  "email": "some.email.address@yahoo.co.uk"
}
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
