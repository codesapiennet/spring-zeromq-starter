package com.example.zeromq.examples;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple immutable message used by the Pub/Sub example.
 */
public final class ExampleMessage {

    private final String id;
    private final String payload;
    private final long timestamp;

    @JsonCreator
    public ExampleMessage(@JsonProperty("id") String id,
                          @JsonProperty("payload") String payload,
                          @JsonProperty("timestamp") long timestamp) {
        this.id = id;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "ExampleMessage{" +
                "id='" + id + '\'' +
                ", payload='" + payload + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
} 