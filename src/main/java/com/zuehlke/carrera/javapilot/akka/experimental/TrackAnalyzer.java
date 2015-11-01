package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.zuehlke.carrera.javapilot.akka.events.TrackRecognizedEvent;

public class TrackAnalyzer extends UntypedActor {


    public static Props props() {
        return Props.create(new Creator<TrackAnalyzer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TrackAnalyzer create() throws Exception {
                return new TrackAnalyzer();
            }
        });
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof TrackRecognizedEvent) {
            handleTrackRecognizedEvent((TrackRecognizedEvent) message);
        }
    }

    private void handleTrackRecognizedEvent(TrackRecognizedEvent message) {
        System.out.println("Track Received");
    }
}
