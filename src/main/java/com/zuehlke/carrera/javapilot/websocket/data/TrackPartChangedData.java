package com.zuehlke.carrera.javapilot.websocket.data;

import com.zuehlke.carrera.javapilot.model.TrackType;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class TrackPartChangedData {
    public final TrackType newTrackType;

    public TrackPartChangedData(TrackType newTrackType) {
        this.newTrackType = newTrackType;
    }
}
