package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.*;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

public class ConstantPowerStrategy implements PowerStrategyInterface {

    private PilotDataEventSender pilotDataEventSender;
    private ActorRef pilotActor;
    private int currentPower;
    private int defaultPower = 110;
    private ActorRef trackAnalyzer;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory ;
    private Track<TrackPart> recognizedTrack;
    private final long maxRoundTime = 100000000;
    private boolean firstRound  = true;
    private LowPassFilter lowPassFilter = new LowPassFilter(100);
    private LowPassFilter crazyPassFilter = new LowPassFilter(60);

    private int throwAwayParts = 0;
    private int startOffset = 8;
    private Pattern trackPattern;
    private LinkedList<PatternAttempt> trackPatternAttempts;
    boolean lapDetected = false;

    private double sum1 = 0.0;
    private double sum2 = 0.0;

    public ConstantPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, ActorRef trackAnalyzer, ActorRef sender, Track<TrackPart> recognizedTrack) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.trackAnalyzer = trackAnalyzer;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(5);
        this.recognizedTrack = recognizedTrack;
        this.trackPatternAttempts = new LinkedList<>();
        this.trackPattern = new Pattern();
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        if(!firstRound) {
            recognizedTrack.addTrackPart(message.getPart());
        }
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize(), message.getPart().id.toString()));

        recognizeLap(message.getPart().getType());
    }

    public void recognizeLap(TrackType trackType){
        Character trackCode = trackType.getCode();
        if(throwAwayParts > 0){
            --throwAwayParts;
            System.out.println("throw away");
        }
        else{
            if(startOffset > 0){
                trackPattern.push(trackCode);
                --startOffset;
                System.out.println("start offset");
            }
            else{
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
                        System.out.println("LAAAAAAAAAAAAAAAAAP");
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

    @Override
    public void handleSensorEvent(final SensorEvent message, final long lastTimestamp, final long timestampDelayThreshold) {

        sum1 += gzDiffHistory.currentStDev();

        if (iAmStillStanding()) {
            increase(5);
            pilotActor.tell(new PowerAction(currentPower), sender);
        } else {
            currentPower = defaultPower;
            pilotActor.tell(new PowerAction(currentPower), sender);
        }
    }

    @Override
    public void handleRoundTime(RoundTimeMessage message) {
    }

    @Override
    public int increase(int val) {
        currentPower += val;
        return currentPower;
    }

    @Override
    public boolean iAmStillStanding() {
        return gzDiffHistory.currentStDev() < 3;
    }

    @Override
    public FloatingHistory getGzDiffHistory() {
        return gzDiffHistory;
    }

    @Override
    public void handlePenaltyMessage(PenaltyMessage message) {
        sum1 -=300;
    }
    @Override
    public int getCurrentPower() {
        return currentPower;
    }
}
