package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.LapCompletedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.model.TrackType;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.CurrentProcessingTrackPart;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class VettelPowerStrategy implements PowerStrategyInterface {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HamiltonPowerStrategy.class);
    private static final int COUNT_OF_TRACKPARTS_TO_COMPARE = 2;
    private static final double REDUCE_SPEED_RATIO_AFTER_PENALTY = 0.90;
    private static final double MAX_SLOWER_RATIO_OF_RACE_TRACKPART = 0.5;
    private static final double MAX_FASTER_RATIO_OF_RACE_TRACKPART = 5.0;
    private static final int INCREASE_AFTER_PENALTY_FREE_ROUND = 8;
    private static final int STRAIGHT_POWER = 255;
    private static final int CURVE_POWER = 120;
    private PilotDataEventSender pilotDataEventSender;

    private ActorRef pilotActor;
    private int defaultPower = 140;
    private int defaultPowerBeforePenalty = 100;
    private int currentPower = defaultPower;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory;
    private Track<TrackPart> currentTrack = new Track<>();
    private HashMap<UUID, Integer> learningMap = new HashMap<>();
    boolean roundWithoutPenalties;
    private final Track<TrackPart> analyzedTrack;
    private ArrayList<ArrayList<TrackPart>> recordedCombinations = new ArrayList<>();

    //TODO Calculate Gforce Limit
    private int gForceLimit = 10000;


    public VettelPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, ActorRef sender, Track<TrackPart> analyzedTrack) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(4);
        this.analyzedTrack = analyzedTrack;
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        currentTrack.addTrackPart(message.getPart());
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize(), message.getPart().id.toString()));
    }

    @Override
    public void handleSensorEvent(SensorEvent message, long lastTimestamp, long timestampDelayThreshold) {

        ArrayList<TrackPart> passedCombination = findCurrentlyPassedCombination();

        TrackPart myPosition = findMyPosition(passedCombination);
        pilotDataEventSender.sendToAll(new CurrentProcessingTrackPart(myPosition));

        if (myPosition != null) {
            currentPower = tryTolookForward(myPosition);
            currentPower = getLearnedPower(myPosition, currentPower);
            currentPower = getPowerFromActualGForce(message.getG()[2], currentPower);

            if (currentPower == -1) {
                currentPower = defaultPower;
            }
        } else {
            currentPower = defaultPower;
            recordedCombinations.add(getLastTrackParts());
        }

        pilotActor.tell(new PowerAction(currentPower), sender);
    }

    private int getPowerFromActualGForce(int gForce, int currentPower) {
        if (gForce > gForceLimit) {
            return 0;
        } else {
            return currentPower;
        }
    }

    private int tryTolookForward(TrackPart myPosition) {
        ArrayList<TrackPart> analyzedTrackParts = analyzedTrack.getTrackParts();

        int indexOfMyPosition = analyzedTrackParts.indexOf(myPosition);

        if (analyzedTrackParts.size() >= indexOfMyPosition + 1 && indexOfMyPosition > 0) {
            TrackType typeOfNextTrackPart = analyzedTrackParts.get(indexOfMyPosition + 1).getType();

            if (typeOfNextTrackPart == TrackType.STRAIGHT) {
                return STRAIGHT_POWER;
            } else {
                return CURVE_POWER;
            }
        } else {
            return -1;
        }
    }

    private int getLearnedPower(TrackPart myPosition, int currentPower) {
        if (learningMap.containsKey(myPosition.id)) {
            return learningMap.get(myPosition.id);
        } else {
            return currentPower;
        }

    }

    private TrackPart findMyPosition(ArrayList<TrackPart> passedCombination) {
        if (passedCombination != null && !passedCombination.isEmpty()) {
            return passedCombination.get(passedCombination.size() - 1);
        } else {
            return null;
        }
    }

    private ArrayList<TrackPart> findCurrentlyPassedCombination() {

        ArrayList<TrackPart> lastMatchingTrackParts = null;

        lastMatchingTrackParts = findTracksInRecordedCombinations(getLastTrackParts());

        if (lastMatchingTrackParts == null) {
            lastMatchingTrackParts = findTracksInAnalyzedTrack(getLastTrackParts());
        }

        return lastMatchingTrackParts;

    }

    private ArrayList<TrackPart> findTracksInAnalyzedTrack(ArrayList<TrackPart> lastTrackParts) {
        ArrayList<TrackPart> analyzedTrackParts = analyzedTrack.getTrackParts();

        for (int i = 0; i < analyzedTrackParts.size(); i++) {
            boolean patternMatches = true;
            for (int j = 0; j < lastTrackParts.size(); j++) {
                if (i + j < analyzedTrackParts.size() && !couldBeSameTrackPart(analyzedTrackParts.get(i + j), lastTrackParts.get(j))) {
                    patternMatches = false;
                    break;
                }
            }

            if (patternMatches) {
                LOGGER.info("=> found matching pattern of trackparts");
                int indexOfLastWantedTrackPart = i + lastTrackParts.size();

                if (indexOfLastWantedTrackPart > analyzedTrackParts.size()) {
                    indexOfLastWantedTrackPart = analyzedTrackParts.size();
                }
                return new ArrayList<>(analyzedTrackParts.subList(i, indexOfLastWantedTrackPart));
            }

        }
        return null;
    }

    private ArrayList<TrackPart> findTracksInRecordedCombinations(ArrayList<TrackPart> currentTrackParts) {
        for (ArrayList<TrackPart> combination : recordedCombinations) {
            boolean patternMatches = true;
            for (int i = 0; i < combination.size(); i++) {
                if (!couldBeSameTrackPart(combination.get(i), currentTrackParts.get(i))) {
                    patternMatches = false;
                    break;
                }
            }

            if (patternMatches) {
                return combination;
            }

        }

        return null;
    }

    private boolean couldBeSameTrackPart(TrackPart analyzedTrackPart, TrackPart currentTrackPart) {
        return analyzedTrackPart.getType() == currentTrackPart.getType()
                && hasAboutSameDuration(analyzedTrackPart.getSize(), currentTrackPart.getSize());
    }

    private boolean hasAboutSameDuration(long constantPowerDuration, long racePowerDuration) {
        return racePowerDuration > constantPowerDuration * MAX_SLOWER_RATIO_OF_RACE_TRACKPART &&
                racePowerDuration < constantPowerDuration * MAX_FASTER_RATIO_OF_RACE_TRACKPART;
    }


    @Override
    public void handlePenaltyMessage(PenaltyMessage message) {
        roundWithoutPenalties = false;
        LOGGER.info("=> Handle penalty {}", message.toString());

        ArrayList<TrackPart> lastMatchingTrackParts = findCurrentlyPassedCombination();

        if (notMatchedToExistingTrackparts(lastMatchingTrackParts)) {
            recordedCombinations.add(getLastTrackParts());
            lastMatchingTrackParts = getLastTrackParts();
        } else {
            LOGGER.info("=> found existing trackparts {}", message.toString());
        }

        reduceSpeed(lastMatchingTrackParts);
    }

    private void reduceSpeed(ArrayList<TrackPart> lastMatchingTrackParts) {

        pilotDataEventSender.sendToAll(new CurrentProcessingTrackPart(lastMatchingTrackParts.get(lastMatchingTrackParts.size() - 1)));

        if (lastMatchingTrackParts.size() > 0) {
            TrackPart beforePenaltyTrackPart = lastMatchingTrackParts.get(lastMatchingTrackParts.size() - 1);
            for (int i = lastMatchingTrackParts.size() - 1; i >= 0; i--) {
                TrackPart trackPart = lastMatchingTrackParts.get(i);

                if (!learningMap.containsKey(trackPart.id)) {
                    learningMap.put(beforePenaltyTrackPart.id, 0);
                    LOGGER.info("=> Reduce Power Value");
                    return;
                }
            }

        }
    }

    private boolean notMatchedToExistingTrackparts(ArrayList<TrackPart> lastMatchingTrackParts) {
        return (lastMatchingTrackParts == null || lastMatchingTrackParts.isEmpty()) && getLastTrackParts().size() > 0;
    }

    private ArrayList<TrackPart> getLastTrackParts() {
        return currentTrack.getLastTrackParts(COUNT_OF_TRACKPARTS_TO_COMPARE);
    }

    @Override
    public int getCurrentPower() {
        return currentPower;
    }

    @Override
    public void handleLapCompletedMessage(LapCompletedEvent message) {

    }

    private void ReduceSpeedForTrackPart(TrackPart beforePenaltyTrackPart) {
        pilotDataEventSender.sendToAll(new CurrentProcessingTrackPart(beforePenaltyTrackPart));
        if (beforePenaltyTrackPart != null) {
            UUID trackPartId = beforePenaltyTrackPart.id;
            if (learningMap.containsKey(trackPartId)) {
                int reducedPowerValue = (int) Math.round(learningMap.get(trackPartId) * REDUCE_SPEED_RATIO_AFTER_PENALTY);
                LOGGER.info("=> Reduce Power Value {}", reducedPowerValue);
                learningMap.put(trackPartId, reducedPowerValue);
            } else {
                LOGGER.info("=> set to default value {}", defaultPowerBeforePenalty);
                learningMap.put(beforePenaltyTrackPart.id, defaultPowerBeforePenalty);
            }
        }
    }

    @Override
    public void handleRoundTime(RoundTimeMessage message) {

        if (roundWithoutPenalties) {

            // TODO Kiru: what is the magic here? copy the map again to itself?
            // No Idea why I needed that :-) Marcel
            for (UUID key : learningMap.keySet()) {
                learningMap.put(key, learningMap.get(key));
            }
            defaultPower += INCREASE_AFTER_PENALTY_FREE_ROUND;
            LOGGER.info("=> Increase Speed {}", defaultPower);
        }

        roundWithoutPenalties = true;
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

    @Override
    public FloatingHistory getGzDiffHistory() {
        return gzDiffHistory;
    }
}
