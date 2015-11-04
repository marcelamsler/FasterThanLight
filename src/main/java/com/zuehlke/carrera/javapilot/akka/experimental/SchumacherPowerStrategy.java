package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.SmoothedSensorInputEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackAnalyzedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.SmoothedSensorData;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;

public class SchumacherPowerStrategy implements PowerStrategyInterface{

    private PilotDataEventSender pilotDataEventSender;
    private ActorRef pilotActor;
    private LowPassFilter lowPassFilter;
    private ActorRef trackPartRecognizer;
    private int currentPower;
    private int defaultPower = 150;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory;
    private Track<TrackPart> analyzedTrack;
    private Track<TrackPart> currentTrack;

    public SchumacherPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, LowPassFilter lowPassFilter, ActorRef trackPartRecognizer, ActorRef sender, FloatingHistory gzDiffHistory, Track<TrackPart> analyzedTrack) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.lowPassFilter = lowPassFilter;
        this.trackPartRecognizer = trackPartRecognizer;
        this.sender = sender;
        this.gzDiffHistory = gzDiffHistory;
        this.analyzedTrack = analyzedTrack;
    }

    @Override
    public void handleTrackAnalyzed(TrackAnalyzedEvent message) {

    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        currentTrack.addTrackPart(message.getPart());
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize()));
    }

    @Override
    public void handleSensorEvent(SensorEvent message, long lastTimestamp, long timestampDelayThreshold) {
        boolean obsoleteMessage = message.getTimeStamp() < lastTimestamp;

        if (obsoleteMessage) {
            return;
        }

        double gz = message.getG()[2];
        gzDiffHistory.shift(gz);

        double smoothValue = lowPassFilter.smoothen(gz, message.getTimeStamp());
        trackPartRecognizer.tell(new SmoothedSensorInputEvent(smoothValue, gz), sender);
        SmoothedSensorData smoothedSensorData = new SmoothedSensorData(smoothValue, currentPower);
        pilotDataEventSender.sendToAll(smoothedSensorData);

        if (iAmStillStanding()) {
            increase(5);
            pilotActor.tell(new PowerAction(currentPower), sender);
        } else {
            currentPower = defaultPower;
            pilotActor.tell(new PowerAction(currentPower), sender);
        }
    }

    @Override
    public int increase(int val) {
        currentPower = currentPower + val;
        return currentPower;
    }

    @Override
    public boolean iAmStillStanding() {
        return gzDiffHistory.currentStDev() < 3;
    }
}
