package com.zuehlke.carrera.javapilot.akka.experimental;

import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;

public interface PowerStrategyInterface {

    void handleTrackPartRecognized(TrackPartRecognizedEvent message);

    void handleSensorEvent(SensorEvent message, long lastTimestamp, long timestampDelayThreshold);

    void handleRoundTime(RoundTimeMessage message);

    int increase(int val);

    boolean iAmStillStanding();

    FloatingHistory getGzDiffHistory();
}
