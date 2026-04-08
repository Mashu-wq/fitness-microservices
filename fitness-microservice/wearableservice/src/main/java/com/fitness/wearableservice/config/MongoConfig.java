package com.fitness.wearableservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig {
    // Activates @CreatedDate on WearableEvent.receivedAt
}
