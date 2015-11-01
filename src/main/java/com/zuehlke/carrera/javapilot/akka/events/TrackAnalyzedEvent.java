package com.zuehlke.carrera.javapilot.akka.events;

import com.zuehlke.carrera.javapilot.model.AnalyzedTrackPart;
import com.zuehlke.carrera.javapilot.model.Track;

public class TrackAnalyzedEvent {

    private final Track<AnalyzedTrackPart> track;

    public TrackAnalyzedEvent(Track<AnalyzedTrackPart> track) {
        this.track = track;
    }

    public Track<AnalyzedTrackPart> getTrack() {
        return track;
    }
}
