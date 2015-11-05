package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;

public class ConstantPowerStrategy implements PowerStrategyInterface {

    private PilotDataEventSender pilotDataEventSender;
    private ActorRef pilotActor;
    private int currentPower;
    private int defaultPower = 130;
    private ActorRef trackAnalyzer;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory ;
    private Track<TrackPart> recognizedTrack;
    private final long maxRoundTime = 100000000;
    private boolean firstRound  = true;

    public ConstantPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, ActorRef trackAnalyzer, ActorRef sender, Track<TrackPart> recognizedTrack) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.trackAnalyzer = trackAnalyzer;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(4);
        this.recognizedTrack = recognizedTrack;
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        if(!firstRound) {
            recognizedTrack.addTrackPart(message.getPart());
        }
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize()));
    }

    @Override
    public void handleSensorEvent(final SensorEvent message, final long lastTimestamp, final long timestampDelayThreshold) {
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
        if(message.getRoundDuration() > maxRoundTime) {
            recognizedTrack.getTrackParts().clear();
        }
        else{
            if(firstRound){
                firstRound = false;
            }
            else {
                trackAnalyzer.tell(new TrackRecognizedEvent(recognizedTrack), sender);
            }
        }
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

    }
}
