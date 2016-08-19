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

    | Path                      | Supported Methods | Description                                                                                                 |
    |---------------------------|-------------------|-------------------------------------------------------------------------------------------------------------|
    | /email-verifications      | POST              | Create a new verification                                                                                   |


## POST /email-verifications

Create a new verification

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

### Response with

    | Status    |  Description                      |
    |-----------|-----------------------------------|
    | 204       | Verification created successfully |
    | 400       | Invalid request                   |
    | 500       | Unexpected error                  |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
