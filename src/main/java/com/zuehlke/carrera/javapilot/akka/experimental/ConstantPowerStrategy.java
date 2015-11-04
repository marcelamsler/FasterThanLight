package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.SmoothedSensorInputEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.SmoothedSensorData;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;

@SuppressWarnings("ALL")
public class ConstantPowerStrategy implements PowerStrategyInterface {

    private PilotDataEventSender pilotDataEventSender;
    private ActorRef pilotActor;
    private LowPassFilter lowPassFilter;
    private ActorRef trackPartRecognizer;
    private int currentPower;
    private int defaultPower = 130;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory ;
    private Track<TrackPart> recognizedTrack;

    public ConstantPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, LowPassFilter lowPassFilter, ActorRef trackPartRecognizer, ActorRef sender, Track<TrackPart> recognizedTrack) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.lowPassFilter = lowPassFilter;
        this.trackPartRecognizer = trackPartRecognizer;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(4);
        this.recognizedTrack = recognizedTrack;
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        recognizedTrack.addTrackPart(message.getPart());
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize()));
    }

    @Override
    public void handleSensorEvent(final SensorEvent message, final long lastTimestamp, final long timestampDelayThreshold) {
        /**
         * Is the case when you get a messageA and then a messageB.
         * You've processed messageB - missed messageA. Now you process messageC.
         * And messageA comes again - then you ignore that
         */
        boolean obsoleteMessage = message.getTimeStamp() < lastTimestamp;

        /**
         * If for long time no message comes
         */
        boolean noMessageForFewMillies = System.currentTimeMillis() - message.getTimeStamp() > timestampDelayThreshold;

        if (obsoleteMessage /*|| noMessageForFewMillies*/) {
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
        currentPower += val;
        return currentPower;
    }

    @Override
    public boolean iAmStillStanding() {
        return gzDiffHistory.currentStDev() < 3;
    }
}
