# email-verification

email-verification is a service to support a verification flow that starts by creating a verification for an email address and sending out an email with verification link.

Users can verify the email address by clicking on the link in the email.

Endpoints are provided to trigger a verification email, verify email by clicking the link and retrieve a verification status for a given email address.


# How to build

Preconditions:
- ```mongod``` needs to be running for it:test

```sbt test it:test```

# API

| Path                             | Supported Methods | Description                                                                              |
|----------------------------------|-------------------|------------------------------------------------------------------------------------------|
| /verification-requests           | POST              | Create a new verification request                                                        |
| /verified-email-check            | POST              | Check if email address is verified                                                       |
| /request-passcode                | POST              | Generates a passcode and sends an email with the passcode to the specified email address |
| /verify-passcode                 | POST              | Verifies the passcode generated against your email address                               |
    

# Test Only Routes

    | Path                             | Supported Methods | Description                                               |
    |----------------------------------|-------------------|-----------------------------------------------------------|
    | /test-only/passcode              | GET              | Retrieves the generated passcode that is stored in mongo                         |
    

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

The template identified by ```templateId``` must contain a parameter named ```verificationLink```. One example is `verifyEmailAddress`.
```linkExpiryDuration``` is the validity in [ISO8601](https://en.wikipedia.org/wiki/ISO_8601#Durations) format.
FYI, if you need to use/add a template, please speak to digital-contact team to get the templateId. 

__Please make sure that you validate your email address before making this request.__

### Success Response

| Status    |  Description                      |
|-----------|-----------------------------------|
| 201       | Verification created successfully |

### Failure Responses

| Status    |  Description                                  |  Code                    |  Note                    |
|-----------|-----------------------------------------------|--------------------------|--------------------------|
| 400       | Invalid request                               | VALIDATION_ERROR         |                          |
| 409       | Email has already been verified               | EMAIL_VERIFIED_ALREADY   |                          |
| 400       | Bad request to email, like template not found | BAD_EMAIL_REQUEST        | This can also happen if the email address is notvalid, this can include leading and/or trailing spaces.                                       |
| 500       | Unexpected error                              | UNEXPECTED_ERROR         |                          |
| 502       | Upstream service error                        | UPSTREAM_ERROR           |                          |


## POST /verified-email-check

Check if email address is verified or not, if verified return 200 with the email.

**Request body**

```json
{
  "email": "some.email.address@yahoo.co.uk"
}
```

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

## GET /test-only/passcode

Retrieves the generated passcode for a given session ID provided in the headers

**Example Request**

```
GET /test-only/passcode

X-Session-ID: SomeSessionId
```

### Success Response

    | Status    |  Description                      |
    |-----------|-----------------------------------|
    | 200       | Found passcode against session ID                 |

### Failure Responses

    | Status    |  Description                      |  Code                            |
    |-----------|-----------------------------------|----------------------------------|
    | 404       | Passcode not found for given session ID    | PASSCODE_NOT_FOUND_OR_EXPIRED  |
    | 400       | Session ID not provided in headers    | BAD_PASSCODE_REQUEST  |

**Response body**

```json
{
  "passcode": "ATERRT"
}
```

## POST /request-passcode
Generates a passcode and sends an email with the passcode to the specified email address


**Request body**

```json
{
    "email": "email@email.com"
}
```

### Success Response

| Status    |  Description                          |
|-----------|---------------------------------------|
| 201       | Passcode is created and email is sent |

### Failure Responses

| Status    |  Description                      |  Code                            |
|-----------|-----------------------------------|----------------------------------|
| 409       | Email already verified            | EMAIL_VERIFIED_ALREADY           |
| 400       | Upstream bad request sending email| BAD_EMAIL_REQUEST                |
| 400       | SessionID not provided            | BAD_REQUEST                      |
| 502       | Upstream error                    | UPSTREAM_ERROR                   |

## POST /verify-passcode
Verifies the passcode generated against your email address 


**Request body**

```json
{
    "passcode": "ABCDEF"
}
```

### Success Response

| Status    |  Description                          |
|-----------|---------------------------------------|
| 201       | Email is successfully verified        |
| 204       | Email is already verified             |

### Failure Responses

| Status    |  Description                      |  Code                            |
|-----------|-----------------------------------|----------------------------------|
| 400       | Passcode not found or expired     | PASSCODE_NOT_FOUND_OR_EXPIRED    |
| 400       | SessionID not provided            | NO_SESSION_ID                    |


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
 
 
