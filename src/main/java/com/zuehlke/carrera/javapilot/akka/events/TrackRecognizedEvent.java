package com.zuehlke.carrera.javapilot.akka.events;

import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;

public class TrackRecognizedEvent {

    private final Track<TrackPart> track;

    public TrackRecognizedEvent(Track<TrackPart> track) {
        this.track = track;
    }

    public Track getTrack() {
        return track;
    }
}
