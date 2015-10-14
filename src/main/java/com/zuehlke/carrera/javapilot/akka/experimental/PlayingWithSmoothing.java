package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.SmoothedSensorInputEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.SmoothedSensorData;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;


public class PlayingWithSmoothing extends UntypedActor {
    public static final int timestampDelayThreshold = 10;
    private final ActorRef pilotActor;
    private final ActorRef trackRecognizer;
    private PilotDataEventSender pilotDataEventSender;
    private int currentPower = 20;

    private int measuringPower = 110;
    private long lastTimestamp = 0;


    private ArrayList<TrackPart> trackParts = new ArrayList<>();

    private LowPassFilter lowPassFilter = new LowPassFilter();

    private FloatingHistory gzDiffHistory = new FloatingHistory(4);

    private int lastValue = 1337;

    public static Props props(ActorRef pilotActor,ActorRef trackRecognizer, PilotDataEventSender pilotDataEventSender) {
        return Props.create(
                PlayingWithSmoothing.class, () ->
                        new PlayingWithSmoothing(pilotActor,trackRecognizer, pilotDataEventSender));
    }

    public PlayingWithSmoothing(ActorRef pilotActor,ActorRef trackRecognizer, PilotDataEventSender pilotDataEventSender) {
        this.pilotActor = pilotActor;
        this.pilotDataEventSender = pilotDataEventSender;
        this.trackRecognizer = trackRecognizer;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof SensorEvent) {
            handleSensorEvent((SensorEvent) message);
        } else if (message instanceof RaceStartMessage) {
            handleRaceStart();
        } else if (message instanceof TrackPartRecognizedEvent){
            handleTrackPartRecognized((TrackPartRecognizedEvent) message);
        }
    }

    private void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        trackParts.add(message.getPart());
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize()));
    }

    private void handleRaceStart() {

    }

    private void handleSensorEvent(SensorEvent message) {
        if (message.getTimeStamp() < lastTimestamp || System.currentTimeMillis() - message.getTimeStamp() > timestampDelayThreshold) {
            return;
        }
        double gz = message.getG()[2];
        gzDiffHistory.shift(gz);
        double smoothValue = lowPassFilter.smoothen(gz, message.getTimeStamp());

        trackRecognizer.tell(new SmoothedSensorInputEvent(smoothValue),getSelf());

        if (iAmStillStanding()) {
            increase(5);
            pilotActor.tell(new PowerAction(currentPower), getSelf());
        } else {
            currentPower = measuringPower;
            pilotActor.tell(new PowerAction(currentPower), getSelf());
        }

        SmoothedSensorData smoothedSensorData = new SmoothedSensorData(smoothValue, currentPower);
        pilotDataEventSender.sendToAll(smoothedSensorData);
    }

    private int increase(int val) {
        currentPower = currentPower + val;
        return currentPower;
    }

    private void show(int gyr2) {
        show(gyr2, "|");
    }

    private void show(int gyr2, String symbol) {
        int scale = 120 * (gyr2 - (-10000)) / 20000;
        System.out.println(StringUtils.repeat(" ", scale) + symbol);
    }

    private void show(int gyr2, int value) {
        int scale = 120 * (gyr2 - (-10000)) / 20000;
        System.out.println(StringUtils.repeat(" ", scale) + value);
    }

    private boolean iAmStillStanding() {
        return gzDiffHistory.currentStDev() < 3;
    }

}
