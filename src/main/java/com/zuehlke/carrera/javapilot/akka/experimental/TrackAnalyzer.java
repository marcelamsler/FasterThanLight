package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.zuehlke.carrera.javapilot.akka.events.TrackAnalyzedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.AnalyzedTrackPart;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;

import java.util.LinkedList;

public class TrackAnalyzer extends UntypedActor {

    private static final double innerRadius = 18;
    private static final double outerRadius = 28;
    public static final double CURVE_MULTIPLIER = 1.1;
    private final ActorRef receiver;

    public TrackAnalyzer(ActorRef receiver) {
        this.receiver = receiver;
    }

    public static Props props(ActorRef receiver) {
        return Props.create(new Creator<TrackAnalyzer>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TrackAnalyzer create() throws Exception {
                return new TrackAnalyzer(receiver);
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
        LinkedList<TrackPart> trackParts = new LinkedList<>(message.getTrack().getTrackParts());
        TrackType innerType = TrackType.UNKNOWN;
        double avgLeft = 0.0;
        int countLeft = 0;
        double avgRight = 0.0;
        int countRight = 0;
        TrackPart previousTrackPart = null;
        for(TrackPart trackPart: trackParts){
            double avg = trackPart.getSize()*trackPart.getMean();
            if(previousTrackPart != null && previousTrackPart.isCurve()){
                avg *= CURVE_MULTIPLIER;
            }
            if(trackPart.getType() == TrackType.RIGHT){
                avgRight += avg;
                countRight += trackPart.getSize();
            }
            else if (trackPart.getType() == TrackType.LEFT){
                avgLeft += avg;
                countLeft += trackPart.getSize();
            }
            previousTrackPart = trackPart;
        }

        if(Math.abs(avgLeft/countLeft) > Math.abs(avgRight/countRight)){
            innerType = TrackType.LEFT;
        }
        else{
            innerType = TrackType.RIGHT;
        }

        Track<AnalyzedTrackPart> analyzedTrack = new Track<>();

        for(TrackPart trackPart: trackParts){
            double length = 0.0;
            int angle = 0;
            double radius = 0.0;
            if (trackPart.isStraight()){
                length = trackPart.getSize() * 5;
            }
            if(trackPart.isCurve()) {
                angle = trackPart.getSize()/8;
                angle = Math.min(8,angle);
                angle = Math.max(1,angle);

                if (trackPart.getType() == TrackType.RIGHT){
                    angle *= -1;
                }

                if (trackPart.getType() == innerType) {
                    System.out.println("inner");
                    radius = innerRadius;
                } else {
                    System.out.println("outer");
                    radius = outerRadius;
                }
            }

            AnalyzedTrackPart analyzedTrackPart = new AnalyzedTrackPart(trackPart.getType(),angle,length,radius);

            analyzedTrack.addTrackPart(analyzedTrackPart);
            System.out.println(trackPart.getType());
            System.out.println(trackPart.getSize());
            System.out.println(trackPart.getMean());
            System.out.println(trackPart.getStdDev());
            System.out.println("------------------");

        }
        receiver.tell(new TrackAnalyzedEvent(analyzedTrack), getSelf());
    }
}
