package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.SmoothedSensorData;
import com.zuehlke.carrera.javapilot.websocket.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;


public class PlayingWithSmoothing extends UntypedActor {
    private final ActorRef pilotActor;
    private PilotDataEventSender pilotDataEventSender;
    private int currentPower = 20;

    private int measuringSpeed = 110;
    private double smoothed = 0.0;

    private int smoothing = 160;
    private double lastTimestamp = 0.0;

    private static final int TRACK_PART_MIN_LENGTH = 3;
    private static final int LINE_TOP_THRESHOLD = 200;

    private ArrayList<TrackPart> trackParts = new ArrayList<>();
    private TrackPart currentTrackPart = new TrackPart();
    private int directionClearanceCount = 0;


    private FloatingHistory gzDiffHistory = new FloatingHistory(4);

    private FloatingHistory actualTrackPart = new FloatingHistory(TRACK_PART_MIN_LENGTH);

    private int lastValue = 1337;

    public PlayingWithSmoothing(ActorRef pilotActor, PilotDataEventSender pilotDataEventSender) {
        this.pilotActor = pilotActor;
        this.pilotDataEventSender = pilotDataEventSender;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof SensorEvent) {
            handleSensorEvent((SensorEvent) message);
        } else if (message instanceof RaceStartMessage) {
            handleRaceStart();
        }
    }

    private void handleRaceStart() {

    }

    private void handleSensorEvent(SensorEvent message) {
        if (message.getTimeStamp() < lastTimestamp || System.currentTimeMillis() - message.getTimeStamp() > 10) {
            return;
        }
        double gz = message.getG()[2];

        double smoothValue = lowPassFilter(gz, message.getTimeStamp());

        boolean directionChanged = directionChanged(smoothValue);
        if (directionChanged) {
            show((int) smoothValue, "-");
        } else {
            show((int) smoothValue);
        }

        evaluateTrackType(smoothValue, directionChanged);

        if (iAmStillStanding()) {
            increase(5);
            pilotActor.tell(new PowerAction(currentPower), getSelf());
        } else {
            currentPower = measuringSpeed;
            pilotActor.tell(new PowerAction(currentPower), getSelf());
        }

        SmoothedSensorData smoothedSensorData = new SmoothedSensorData(smoothValue, currentPower);
        pilotDataEventSender.sendToAll(smoothedSensorData);
    }

    private boolean directionChanged(double smoothValue) {
        boolean directionChanged = false;
        if (smoothValue > -LINE_TOP_THRESHOLD && smoothValue < LINE_TOP_THRESHOLD) {
            directionChanged = lastValue != 0;
            lastValue = 0;
        } else if (smoothValue > LINE_TOP_THRESHOLD) {
            directionChanged = lastValue != 1;
            lastValue = 1;
        } else if (smoothValue < -LINE_TOP_THRESHOLD) {
            directionChanged = lastValue != -1;
            lastValue = -1;
        }

        return directionChanged;
    }

    private void evaluateTrackType(double smoothValue, boolean forceEvaluation) {
        if(forceEvaluation){
            directionClearanceCount = 0;
            if(currentTrackPart.getSensorValues().size() >= TRACK_PART_MIN_LENGTH){
                TrackType type = decideTrackPartType(currentTrackPart.getMean());
                currentTrackPart.setType(type);
                currentTrackPart = new TrackPart();

                pilotDataEventSender.sendToAll(new TrackPartChangedData(type));
                trackParts.add(currentTrackPart);
            }
            actualTrackPart = new FloatingHistory(TRACK_PART_MIN_LENGTH);
        }
        actualTrackPart.shift(smoothValue);
        currentTrackPart.pushSensorValue(smoothValue);
        directionClearanceCount++;

    }

    public TrackType decideTrackPartType(double mean){
        if (mean > -LINE_TOP_THRESHOLD && mean < LINE_TOP_THRESHOLD) {
            System.out.println("That was a line");
            return TrackType.STRAIGHT;
        }
        else if (mean > LINE_TOP_THRESHOLD) {
            System.out.println("That was a right curve");
            return TrackType.RIGHT;
        } else if (mean < -LINE_TOP_THRESHOLD) {
            System.out.println("That was a left curve");
            return TrackType.LEFT;
        }
        return TrackType.UNKNOWN;
    }

    public static Props props(ActorRef pilotActor, PilotDataEventSender pilotDataEventSender) {
        return Props.create(
                PlayingWithSmoothing.class, () ->
                        new PlayingWithSmoothing(pilotActor, pilotDataEventSender));
    }

    private double lowPassFilter(double value, long currentTimestamp) {
        double elapsedTime = currentTimestamp - lastTimestamp;
        double diff = value - smoothed;
        if (elapsedTime < 60) {
            smoothed += elapsedTime * diff / smoothing;
        } else {
            smoothed += diff / (smoothing / 50);
        }
        lastTimestamp = currentTimestamp;
        return smoothed;
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
