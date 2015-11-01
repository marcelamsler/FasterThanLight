package com.zuehlke.carrera.javapilot.model;

import java.util.ArrayList;


public class Track {

    ArrayList<TrackPart>  trackParts;

    public Track(){
        trackParts = new ArrayList<>();
    }

    public void addTrackPart(TrackPart trackPart){
        trackParts.add(trackPart);
    }
}
