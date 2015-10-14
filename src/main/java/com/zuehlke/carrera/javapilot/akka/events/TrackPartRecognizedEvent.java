package com.zuehlke.carrera.javapilot.akka.events;

import com.zuehlke.carrera.javapilot.model.TrackPart;

public class TrackPartRecognizedEvent {
    private final TrackPart part;

    public TrackPartRecognizedEvent(TrackPart part) {
        this.part = part;
    }

    public TrackPart getPart() {
        return part;
    }
}
