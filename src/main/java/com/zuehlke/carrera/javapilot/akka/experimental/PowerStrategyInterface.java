package com.zuehlke.carrera.javapilot.akka.experimental;

import com.zuehlke.carrera.javapilot.akka.events.TrackAnalyzedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;

public interface PowerStrategyInterface {
    void handleTrackAnalyzed(TrackAnalyzedEvent message);

    void handleTrackPartRecognized(TrackPartRecognizedEvent message);

    void handleSensorEvent(SensorEvent message, long lastTimestamp, long timestampDelayThreshold);

    int increase(int val);

    boolean iAmStillStanding();
}
