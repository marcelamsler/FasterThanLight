package com.zuehlke.carrera.javapilot.akka.events;

import com.zuehlke.carrera.javapilot.model.Track;

public class TrackRecognizedEvent {

    private final Track track;

    public TrackRecognizedEvent(Track track) {
        this.track = track;
    }

    public Track getTrack() {
        return track;
    }
}
