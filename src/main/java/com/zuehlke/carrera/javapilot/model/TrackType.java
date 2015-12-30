package com.zuehlke.carrera.javapilot.model;

public enum TrackType {
    UNKNOWN('u'),
    LEFT('l'),
    RIGHT('r'),
    STRAIGHT('s');

    private Character code;
    TrackType(Character code){
        this.code=code;
    }
    public Character getCode() {
        return this.code;
    }
}
