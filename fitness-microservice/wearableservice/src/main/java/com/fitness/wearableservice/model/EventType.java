package com.fitness.wearableservice.model;

public enum EventType {
    HEART_RATE,     // real-time bpm reading
    STEPS,          // step count snapshot
    GPS_TRACK,      // latitude/longitude point
    SLEEP,          // sleep stage/duration data
    CALORIES,       // calorie burn snapshot
    WORKOUT_START,  // marks beginning of a tracked workout session
    WORKOUT_END     // marks end — triggers aggregation → activity creation
}
