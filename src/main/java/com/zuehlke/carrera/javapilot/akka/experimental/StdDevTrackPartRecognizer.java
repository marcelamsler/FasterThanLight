package com.zuehlke.carrera.javapilot.akka.experimental;


import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.zuehlke.carrera.javapilot.akka.events.SmoothedSensorInputEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.timeseries.FloatingHistory;

public class StdDevTrackPartRecognizer extends UntypedActor {

    private LowPassFilter crazyPassFilter = new LowPassFilter(150);
    private final ActorRef receiver;
    private double trackPartLength;
    private int numberOfParts = 200;
    private static final int LINE_TOP_THRESHOLD = 200;
    private TrackPart currentTrackPart = new TrackPart();
    private FloatingHistory gzDiffHistory ;
    private double stdDevCounter = 0.0;
    private long startTimestamp;


    public static Props props(ActorRef receiver,double trackLength) {
        return Props.create(new Creator<StdDevTrackPartRecognizer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public StdDevTrackPartRecognizer create() throws Exception {
                return new StdDevTrackPartRecognizer(receiver,trackLength);
            }
        });
    }
    public  StdDevTrackPartRecognizer(ActorRef receiver,double trackLength){
        this.receiver = receiver;
        this.trackPartLength = trackLength/numberOfParts;
        this.gzDiffHistory = new FloatingHistory(5);

    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof SmoothedSensorInputEvent) {
            handleSmoothedSensorInputEvent((SmoothedSensorInputEvent) message);
        }
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

    private void handleSmoothedSensorInputEvent(SmoothedSensorInputEvent event) {

        gzDiffHistory.shift(event.getValue());

        double crazyValue = crazyPassFilter.smoothen(gzDiffHistory.currentStDev(),event.getTimestamp());
        stdDevCounter += crazyValue;

        if(stdDevCounter >= trackPartLength){
            finishCurrentTrackPart();
            startNewTrackPart();
            stdDevCounter = stdDevCounter - trackPartLength;
            currentTrackPart.pushSensorValue(event.getValue(),event.getRaw());
        }
    }

    private void finishCurrentTrackPart() {
        currentTrackPart.setType(decideTrackPartType(currentTrackPart.getMean()));
        currentTrackPart.setDuration(System.currentTimeMillis()-startTimestamp);
        receiver.tell(new TrackPartRecognizedEvent(currentTrackPart),getSelf());
    }

    private void startNewTrackPart() {
        currentTrackPart = new TrackPart();
        startTimestamp = System.currentTimeMillis();
    }
}
