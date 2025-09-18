# zeromq-examples

This module contains runnable example Spring components that demonstrate common ZeroMQ patterns using the `ZeroMqTemplate`.

Included examples:

- `PubSubExample` - basic publish/subscribe flow
- `RequestReplyExample` - REQ/REP synchronous request-reply
- `PushPullExample` - PUSH/PULL task distribution
- `LocalComputeExample` - compute example (existing)
- `SecurityExample` - shows conditional publish using CURVE/PLAIN based on properties

To run examples, start the sample application which includes these components:

```bash
mvn -pl sample-app spring-boot:run
```

Adjust endpoints in `sample-app/src/main/resources/application.yml` if needed. 