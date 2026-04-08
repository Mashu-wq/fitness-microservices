package com.fitness.goalservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig {
    // @CreatedDate and @LastModifiedDate on Goal and GoalProgress are activated here
}
