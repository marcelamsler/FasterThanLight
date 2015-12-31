package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.zuehlke.carrera.javapilot.akka.events.LapCompletedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackAnalyzedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.*;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;

import java.util.LinkedList;

public class TrackAnalyzer extends UntypedActor {

    private int throwAwayParts;
    private int startOffset;
    private Character startOffsetChar;
    private Pattern trackPattern;
    private LinkedList<PatternAttempt> trackPatternAttempts;
    boolean lapDetected = false;
    private final ActorRef receiver;

    public TrackAnalyzer(ActorRef receiver) {

        this.receiver = receiver;
        this.trackPatternAttempts = new LinkedList<>();
        this.trackPattern = new Pattern();
        throwAwayParts = 0;
        startOffset = 8;
        startOffsetChar = null;
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
        if (message instanceof TrackPartRecognizedEvent) {
            handleTrackPartRecognized((TrackPartRecognizedEvent) message);
        }
    }

    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        recognizeLap(message.getPart().getType());
    }

    public void recognizeLap(TrackType trackType){
        System.out.println(trackPatternAttempts.size());
        System.out.println(trackPattern);
        Character trackCode = trackType.getCode();
        if(throwAwayParts > 0){
            --throwAwayParts;
            System.out.println("throw away");
        }
        else{
            if(startOffset > 0 && !trackCode.equals(startOffsetChar)){
                trackPattern.push(trackCode);
                startOffsetChar = trackCode;
                --startOffset;
            }
            else if (startOffset <= 0) {
                if(trackPattern.getFirstElement().equals(trackCode)){
                    PatternAttempt attempt = new PatternAttempt();
                    if(!trackPatternAttempts.isEmpty())
                        trackPatternAttempts.getLast().makeDiff();
                    trackPatternAttempts.add(attempt);
                }
                if(trackPatternAttempts.isEmpty()){
                    trackPattern.push(trackCode);
                }
                else {
                    for (PatternAttempt attempt : trackPatternAttempts) {
                        attempt.push(trackCode);
                    }

                    lapDetected = detectLap();

                    if(lapDetected){
                        receiver.tell(new LapCompletedEvent(),getSelf());
                        System.out.println("LAP!");
                        trackPatternAttempts.clear();
                        trackPattern.setComplete();
                        lapDetected = false;
                    }
                }
            }
        }
    }

    public boolean detectLap(){
        while (!trackPatternAttempts.isEmpty()){
            boolean patternMatches = trackPattern.match(trackPatternAttempts.getFirst());
            boolean sameLength = trackPattern.length() == trackPatternAttempts.getFirst().length();

            if(patternMatches && sameLength){
                return true;
            }
            else if(patternMatches){
                return false;
            }
            else{
                PatternAttempt removedAttempt = trackPatternAttempts.poll();
                trackPattern.push(removedAttempt.getDiff());
            }
        }
        return false;
    }
}
