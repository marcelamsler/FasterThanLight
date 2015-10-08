package com.zuehlke.carrera.javapilot.websocket;

import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;

import java.util.ArrayList;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class TrackPartChangedData {
    public final TrackType newTrackType;

    public TrackPartChangedData(TrackType newTrackType) {
        this.newTrackType = newTrackType;
    }
}
