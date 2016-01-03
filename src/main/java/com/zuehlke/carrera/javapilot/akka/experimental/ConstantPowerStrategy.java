package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.LapCompletedEvent;
import com.zuehlke.carrera.javapilot.akka.events.LengthOfTrackComputedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.math3.stat.StatUtils;

public class ConstantPowerStrategy implements PowerStrategyInterface {

    private PilotDataEventSender pilotDataEventSender;
    private ActorRef pilotActor;
    private int currentPower;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory ;
    private int numberOfLapsRequired = 1;  //should be >= 1
    private boolean firstLap = true;
    private double[] lapLengths = new double[numberOfLapsRequired];

    private double trackLength = 0.0;

    public ConstantPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, ActorRef sender) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(5);
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize(), message.getPart().id.toString()));
    }

    @Override
    public void handleSensorEvent(final SensorEvent message, final long lastTimestamp, final long timestampDelayThreshold) {

        trackLength += gzDiffHistory.currentStDev();

        if (iAmStillStanding()) {
            increase(5);
            pilotActor.tell(new PowerAction(currentPower), sender);
        }
        pilotActor.tell(new PowerAction(currentPower), sender);
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
        trackLength -=300;
    }
    @Override
    public int getCurrentPower() {
        return currentPower;
    }

    @Override
    public void handleLapCompletedMessage(LapCompletedEvent message) {
        if(!firstLap) {
            if (numberOfLapsRequired > 0) {
                lapLengths[numberOfLapsRequired-1] = trackLength;
                --numberOfLapsRequired;
            } else {
                sender.tell(new LengthOfTrackComputedEvent(StatUtils.mean(lapLengths)),sender);
            }
        }
        else{
            System.out.println("firstLap");
            firstLap = false;
        }

        trackLength = 0;
    }
}
