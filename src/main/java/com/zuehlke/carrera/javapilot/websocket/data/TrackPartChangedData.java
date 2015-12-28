package com.zuehlke.carrera.javapilot.websocket.data;

import com.zuehlke.carrera.javapilot.model.TrackType;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class TrackPartChangedData {
    public final TrackType newTrackType;
    public final int size;
    public String id;

    public TrackPartChangedData(TrackType newTrackType, int size, String id) {
        this.newTrackType = newTrackType;
        this.size = size;
        this.id = id;
    }
}
