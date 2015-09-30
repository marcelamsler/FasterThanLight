package com.zuehlke.carrera.javapilot.services;

import com.zuehlke.carrera.timeseries.FloatingHistory;

public class ExtendedFloatingHistory extends FloatingHistory{

    public ExtendedFloatingHistory(int size) {
        super(size);
    }

    public ExtendedFloatingHistory(int size, double def) {
        super(size);

        for(int i = 0; i < size; i++) {
            this.shift(def);
        }
    }


}
