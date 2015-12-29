package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;

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
    private int throwAwayParts = 3;
    private int startOffset = 2;
    private LinkedList<TrackType> trackPattern;
    private ListIterator<TrackType> trackPatternPosition;
    private LinkedList<TrackType> trackFindingPattern;
    private boolean findingStarted;

    private double sum1 = 0.0;
    private double sum2 = 0.0;

    public ConstantPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, ActorRef trackAnalyzer, ActorRef sender, Track<TrackPart> recognizedTrack) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.trackAnalyzer = trackAnalyzer;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(5);
        this.recognizedTrack = recognizedTrack;
        this.trackPattern = new LinkedList<>();
        this.trackFindingPattern = new LinkedList<>();
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        if(!firstRound) {
            recognizedTrack.addTrackPart(message.getPart());
        }
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize(), message.getPart().id.toString()));
        for (TrackType type : trackPattern){
            System.out.println(type);
        }
        System.out.println("-------------------------------------");
        TrackPart part = message.getPart();
        if(throwAwayParts > 0){
            --throwAwayParts;
            System.out.println("throw away");
        }
        else{
            if(startOffset > 0){
                trackPattern.addLast(part.getType());
                --startOffset;
                System.out.println("start offset");
            }
            else{
                if(findingStarted){
                    if(trackPatternPosition.hasNext()) {
                        if(trackFindingPattern.getLast() != part.getType()) {
                            trackFindingPattern.add(part.getType());
                        }
                        if (part.getType() != trackPatternPosition.next()){
                            trackPattern.addAll(trackFindingPattern);
                            trackFindingPattern.clear();
                            findingStarted = false;
                        }
                    }
                    else {
                        System.out.println("Lap Detected");
                        System.out.println("Lap Detected");
                        System.out.println("Lap Detected");
                        System.out.println("Lap Detected");
                        System.out.println("Lap Detected");
                    }
                }
                else if(trackPattern.getFirst() == part.getType()){
                    findingStarted = true;
                    trackPatternPosition = trackPattern.listIterator();
                    trackPatternPosition.next();
                    trackFindingPattern.add(part.getType());
                    System.out.println("findingStarted");
                }
                else {
                    if(trackPattern.getLast() != part.getType()){
                        trackPattern.addLast(part.getType());
                    }
                }

            }
        }
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
