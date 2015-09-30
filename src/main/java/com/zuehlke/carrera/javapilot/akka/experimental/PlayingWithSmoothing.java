package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.services.ExtendedFloatingHistory;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.lang.StringUtils;


public class PlayingWithSmoothing extends UntypedActor {
    private final ActorRef marco;
    private int currentPower = 20;

    private int measuringSpeed = 110;
    private double smoothed = 0.0;

    private int smoothing = 160;
    private double lastTimestamp = 0.0;

    private static final int TRACK_PART_LENGTH = 50;
    private static final int TRACK_PART_MIN_LENGTH = 5;
    private static final int LINE_TOP_THRESHOLD = 200;
    private static final int DIRECTION_CHANGED_COUNT = 5;


    private FloatingHistory gzDiffHistory = new FloatingHistory(4);

    private FloatingHistory actualTrackPart = new FloatingHistory(TRACK_PART_LENGTH);

    private int countActualPart;
    private  int lastValue = 0;

    public PlayingWithSmoothing(ActorRef pilotActor) {
        this.marco = pilotActor;
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
        double previousSmoothed = smoothed;

        double smoothValue = lowPassFilter(gz, message.getTimeStamp());
        double smoothDiff = previousSmoothed - smoothValue;

        if (directionChanged(smoothValue)) {
            show((int) smoothValue, "-");
        } else {
            show((int) smoothValue);
        }

        evaluateTrackType(smoothValue, directionChanged(smoothValue));

        gzDiffHistory.shift(smoothDiff);

        if (iAmStillStanding()) {
            increase(5);
            marco.tell(new PowerAction(currentPower), getSelf());
        } else {
            currentPower = measuringSpeed;
            marco.tell(new PowerAction(currentPower), getSelf());
        }
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
        actualTrackPart.shift(smoothValue);
        countActualPart++;

        if (countActualPart == TRACK_PART_LENGTH || (forceEvaluation && countActualPart > TRACK_PART_MIN_LENGTH) ) {
            double currentMean = actualTrackPart.currentMean();
            if (currentMean > -LINE_TOP_THRESHOLD && currentMean < LINE_TOP_THRESHOLD) {
                System.out.println("That was a line");
            } else if (currentMean > LINE_TOP_THRESHOLD) {
                System.out.println("That was a right curve");
            } else if (currentMean < -LINE_TOP_THRESHOLD) {
                System.out.println("That was a left curve");
            }
            countActualPart = 0;
            actualTrackPart = new FloatingHistory(50);
        }
    }

    public static Props props(ActorRef pilotActor) {
        return Props.create(
                PlayingWithSmoothing.class, () -> new PlayingWithSmoothing(pilotActor));
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
