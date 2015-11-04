package com.zuehlke.carrera.javapilot.model;

import java.util.ArrayList;
import java.util.List;


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

    public ArrayList<T> getLastTrackParts(int count) {
        return (ArrayList<T>) trackParts.subList(Math.max(trackParts.size() - count, 0), trackParts.size());
    }
}
