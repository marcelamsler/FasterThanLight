package com.zuehlke.carrera.javapilot.websocket.data;

import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class CurrentProcessingTrackPart {
    public final TrackPart trackPart;

    public CurrentProcessingTrackPart(TrackPart trackPart) {
        this.trackPart = trackPart;
    }
}
