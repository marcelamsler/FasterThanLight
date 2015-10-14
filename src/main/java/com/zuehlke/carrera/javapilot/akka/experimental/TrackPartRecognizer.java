package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.zuehlke.carrera.javapilot.akka.events.SmoothedSensorInputEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;

public class TrackPartRecognizer extends UntypedActor {

    private static final int TRACK_PART_MIN_LENGTH = 3;
    private static final int LINE_TOP_THRESHOLD = 200;
    private TrackPart currentTrackPart = new TrackPart();
    private int lastValue;


    public static Props props() {
        return Props.create(new Creator<TrackPartRecognizer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TrackPartRecognizer create() throws Exception {
                return new TrackPartRecognizer();
            }
        });
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof SmoothedSensorInputEvent) {
            handleSmoothedSensorInputEvent((SmoothedSensorInputEvent) message);
        }
    }

    private void handleSmoothedSensorInputEvent(SmoothedSensorInputEvent message) {
        double smoothValue = message.getValue();
        if(directionChanged(smoothValue) && currentTrackPart.getSensorValues().size() >= TRACK_PART_MIN_LENGTH){
            evaluateTrackType();
            getSender().tell(new TrackPartRecognizedEvent(currentTrackPart),getSelf());
            currentTrackPart = new TrackPart();
        }
        currentTrackPart.pushSensorValue(smoothValue);
    }


    private boolean directionChanged(double smoothValue) {
        boolean directionChanged = false;
        if (smoothValue > -LINE_TOP_THRESHOLD && smoothValue < LINE_TOP_THRESHOLD) {
            directionChanged = lastValue != 0;
            lastValue = 0;
        } else if (smoothValue > LINE_TOP_THRESHOLD) {
            directionChanged = lastValue != 1;
            lastValue = 1;
        } else if (smoothValue < -LINE_TOP_THRESHOLD) {
            directionChanged = lastValue != -1;
            lastValue = -1;
        }
        return directionChanged;
    }

    public TrackType decideTrackPartType(double mean){
        if (mean > -LINE_TOP_THRESHOLD && mean < LINE_TOP_THRESHOLD) {
            return TrackType.STRAIGHT;
        }
        else if (mean > LINE_TOP_THRESHOLD) {
            return TrackType.RIGHT;
        } else if (mean < -LINE_TOP_THRESHOLD) {
            return TrackType.LEFT;
        }
        return TrackType.UNKNOWN;
    }

    private void evaluateTrackType() {
        TrackType type = decideTrackPartType(currentTrackPart.getMean());
        currentTrackPart.setType(type);
    }

}
