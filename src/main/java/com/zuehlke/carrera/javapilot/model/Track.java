package com.zuehlke.carrera.javapilot.model;

import java.util.ArrayList;


public class Track<T extends TrackPart> {

    protected ArrayList<T>  trackParts;

    public Track(){
        trackParts = new ArrayList<>();
    }

    public void addTrackPart(T trackPart){
        trackParts.add(trackPart);
    }

    public ArrayList<T> getTrackParts(){
        return trackParts;
    }
}
