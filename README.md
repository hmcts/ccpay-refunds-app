# ccpay-refunds-app template

[![Build Status](https://travis-ci.org/hmcts/ccpay-refunds-app.svg?branch=master)](https://travis-ci.org/hmcts/ccpay-refunds-app)

## Notes

Since Spring Boot 2.1 bean overriding is disabled. If you want to enable it you will need to set `spring.main.allow-bean-definition-overriding` to `true`.

JUnit 5 is now enabled by default in the project. Please refrain from using JUnit4 and use the next generation

## Building and deploying the application

### Building the application

The project uses [Gradle](https://gradle.org) as a build tool. It already contains
`./gradlew` wrapper script, so there's no need to install gradle.

To build the project execute the following command:

```bash
  ./gradlew build
```

### Running the application

Create the image of the application by executing the following command:

```bash
  ./gradlew assemble
```

Create docker image:

```bash
  docker-compose build
```

Run the distribution (created in `build/install/refunds-app` directory)
by executing the following command:

```bash
  docker-compose up
```

This will start the API container exposing the application's port
(set to `8081` in this template app).

In order to test if the application is up, you can call its health endpoint:

```bash
  curl http://localhost:8081/health
```

You should get a response similar to this:

```
  {"status":"UP","diskSpace":{"status":"UP","total":249644974080,"free":137188298752,"threshold":10485760}}
```

### Alternative script to run application

To skip all the setting up and building, just execute the following command:

```bash
./bin/run-in-docker.sh
```

For more information:

```bash
./bin/run-in-docker.sh -h
```

Script includes bare minimum environment variables necessary to start api instance. Whenever any variable is changed or any other script regarding docker image/container build, the suggested way to ensure all is cleaned up properly is by this command:

```bash
docker-compose rm
```

It clears stopped containers correctly. Might consider removing clutter of images too, especially the ones fiddled with:

```bash
docker images

docker image rm <image-id>
```

There is no need to remove postgres and java or similar core images.


### Rate Limiting
All API endpoints within this application have rate limiting applied.

The limit is configured through three resiliance4j parameters, which may be configured from environment variables:
```
REFUNDS_API_RATE_LIMIT_FOR_PERIOD  (default = 10)
REFUNDS_API_RATE_LIMIT_REFRESH_PERIOD  (default = 1 second)
REFUNDS_API_RATE_LIMIT_TIMEOUT_DURATION   (default = 0)
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Payment service authorization and S2S compatibility (CME-898 / CME-900)

`PATCH /payment/{paymentReference}/action/cancel` requires a valid `ServiceAuthorization` token
from a configured allowed service. It does not require an IDAM user token. The service filter
validates the token without establishing a Spring user login, so the payment security chain uses
an explicit service authorization manager instead of a user `.authenticated()` rule. User tokens
and forwarded certificate headers alone cannot authorize cancellation.

S2S `/lease` uses six-digit HMAC-SHA1 TOTP with a 30-second step, matching
`service-auth-provider-java-client`'s `TotpGenerator` and
[payment-api-gateway PR #139](https://github.com/hmcts/ccpay-payment-api-gateway/pull/139/changes).
Changing only the gateway to HMAC-SHA256 breaks this shared protocol; it does not change the algorithm
used to sign S2S JWTs. A non-200 lease response now produces a gateway failure instead of reaching the
backend as a malformed bearer token. Restoring compatibility does not close the broader weak-cryptography
finding: a coordinated S2S protocol upgrade remains separate work for CME-900.

Local checks:

```sh
./gradlew test --tests '*PaymentServiceSecurityTest' --tests '*RefundStatusUpdateAuthorizationManagerTest'
python3 -B -m unittest discover -s infrastructure/tests -v
```

The Java tests exercise the real cancellation security chain with a stubbed S2S validator. The Python
checks validate policy structure without executing APIM's C# expressions. After deploying to Demo,
verify cancellation with valid, missing, invalid and unapproved service tokens, and confirm failed
S2S leasing cannot forward an invalid `ServiceAuthorization` token. Certificate and trusted-source
settings are documented in [infrastructure/README.md](infrastructure/README.md) and belong to CME-899.
