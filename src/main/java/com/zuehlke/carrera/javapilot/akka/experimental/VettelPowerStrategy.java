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
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.relayapi.messages.VelocityMessage;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class VettelPowerStrategy implements PowerStrategyInterface {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(VettelPowerStrategy.class);
    private static final int COUNT_OF_TRACKPARTS_TO_COMPARE = 100;
    private static final int INCREASE_PER_SUCCESSFUL_ROUND = 15;
    private static final int COUNT_OF_FORWARD_LOOKING_TRACKPARTS = 4;
    private static final int BRAKE_POWER = 1;
    private static final int FAILURE_TOLERANCE_FOR_TRACKPART_MATCHING_IN_PERCENT = 10;

    private static final int ACCELERATION_DURING_TURN = 15;
    public static final double MAX_TURN_GFORCE_RATIO = 1.2;
    private static final int MAX_STRAIGHT_POWER = 255;
    private static final int MAX_TURN_POWER = 200 ;

    private PilotDataEventSender pilotDataEventSender;
    private ActorRef pilotActor;
    private int defaultPower = 130;
    private int currentPower = defaultPower;
    private int fullPower = defaultPower + 10;
    private int turnPower = defaultPower - 50;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory;
    private Track<TrackPart> currentTrack = new Track<>();
    private HashMap<UUID, Integer> learningMap = new HashMap<>();
    private ArrayList<ArrayList<TrackPart>> recordedCombinations = new ArrayList<>();
    private boolean roundWithoutPenalties;
    private double maxMeasuredSpeed = 0;


    private int currentSpeed = 0;
    private int slowSpeed = 0;
    private int lastPower = 0;


    public VettelPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, ActorRef sender) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(4);
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        currentTrack.addTrackPart(message.getPart());
        addToPreviousCombinations(message.getPart());
//        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize(), message.getPart().id.toString()));
    }

    private void addToPreviousCombinations(TrackPart trackPart) {
        if (recordedCombinations.size() > 0) {
            for (int i = recordedCombinations.size() - 1; i > 0; i--) {
                ArrayList<TrackPart> previousCombination = recordedCombinations.get(i);

                if (previousCombination.size() < COUNT_OF_TRACKPARTS_TO_COMPARE + COUNT_OF_FORWARD_LOOKING_TRACKPARTS) {
                    previousCombination.add(trackPart);
                }
            }
        }
    }

    @Override
    public void handleSensorEvent(SensorEvent message, long lastTimestamp, long timestampDelayThreshold) {

        ArrayList<TrackPart> passedCombination = findCurrentlyPassedCombination();

        TrackPart myPosition = findMyPosition(passedCombination);

        if (myPosition != null) {
            pilotDataEventSender.sendToAll(new CurrentProcessingTrackPart(myPosition));

            currentPower = SetPowerAccordingToCurrentSpeedAndFutureTrackParts(passedCombination, currentSpeed);
            if (currentPower == -1) {
                currentPower = defaultPower;
            }
           currentPower = adjustPowerBasedOnCurrentGForce(message.getG()[2], myPosition, currentPower);
//            LOGGER.info("=> currentSpeed {}", currentSpeed);
            currentPower = getLearnedPowerIfAvailable(myPosition, currentSpeed, currentPower);
        } else {
            currentPower = defaultPower;
            recordThisCombination();
        }

        updateCurrentSpeed(currentPower);
        lastPower = currentPower;

        pilotActor.tell(new PowerAction(currentPower), sender);
    }

    private void recordThisCombination() {
        if (getLastTrackParts() != null && getLastTrackParts().size() == COUNT_OF_TRACKPARTS_TO_COMPARE) {
            recordedCombinations.add(getLastTrackParts());
        }
    }

    private int SetPowerAccordingToCurrentSpeedAndFutureTrackParts(ArrayList<TrackPart> recordedCombination, int currentSpeed) {

        if (recordedCombination.size() == COUNT_OF_TRACKPARTS_TO_COMPARE + COUNT_OF_FORWARD_LOOKING_TRACKPARTS) {
            return estimatePossiblePower(recordedCombination, currentSpeed);
        } else {
            return -1;
        }
    }

    private int adjustPowerBasedOnCurrentGForce(int gForce, TrackPart myPosition, int currentPower) {
        if (myPosition.isCurve()) {
            int gForceLimit = Double.valueOf(myPosition.getMax() * MAX_TURN_GFORCE_RATIO).intValue();
            if (Math.abs(gForce) > gForceLimit) {
                return Double.valueOf(addToPower(lastPower, -ACCELERATION_DURING_TURN, MAX_TURN_POWER)).intValue();
            } else {
                return Double.valueOf(addToPower(lastPower, ACCELERATION_DURING_TURN, MAX_TURN_POWER)).intValue();
            }

        }
        return currentPower;

    }

    private int getLearnedPowerIfAvailable(TrackPart myPosition, int currentSpeed, int currentPower) {
        if (learningMap.containsKey(myPosition.id)) {
            if (iAmReallySlow()) {
                return defaultPower;
            } else {
                return learningMap.get(myPosition.id);
            }
        } else {
            return currentPower;
        }
    }

    private int estimatePossiblePower(ArrayList<TrackPart> recordedCombination, int currentSpeed) {
        TrackType nextTrackPartType = recordedCombination.get(recordedCombination.size() - 1).getType();

        boolean straightAhead = nextTrackPartType == TrackType.STRAIGHT;
        boolean turnAhead = (nextTrackPartType == TrackType.LEFT || nextTrackPartType == TrackType.RIGHT);


        if (straightAhead) {
            return fullPower;
        } else if (turnAhead) {
            if (iAmReallySlow()) {
                return turnPower;
            } else {
                return BRAKE_POWER;
            }
        } else {
            return defaultPower;
        }

    }

    private TrackPart findMyPosition(ArrayList<TrackPart> passedCombination) {
        if (passedCombination != null && !passedCombination.isEmpty()) {
            return passedCombination.get(passedCombination.size() - (1 + COUNT_OF_FORWARD_LOOKING_TRACKPARTS));
        } else {
            return null;
        }
    }

    private ArrayList<TrackPart> findCurrentlyPassedCombination() {
        return findTracksInRecordedCombinations(getLastTrackParts());
    }

    private ArrayList<TrackPart> findTracksInRecordedCombinations(ArrayList<TrackPart> currentTrackParts) {

        HashMap<Integer, ArrayList<TrackPart>> matchingCombinations = new HashMap<>();

        for (ArrayList<TrackPart> combination : recordedCombinations) {
            boolean patternMatches = true;
            int wrongMatches = 0;
            for (int i = 0; i < combination.size() - COUNT_OF_FORWARD_LOOKING_TRACKPARTS; i++) {
                if (!couldBeSameTrackPart(combination.get(i), currentTrackParts.get(i))) {
                    wrongMatches++;
                    if (wrongMatches > COUNT_OF_TRACKPARTS_TO_COMPARE / 100.0 * FAILURE_TOLERANCE_FOR_TRACKPART_MATCHING_IN_PERCENT) {
                        patternMatches = false;
                    }
                }
            }

            if (patternMatches) {
                matchingCombinations.put(wrongMatches, combination);
            }

        }

        if (!matchingCombinations.isEmpty()) {
            LOGGER.info("=> Found matching Combinations {}", matchingCombinations.size());
            return matchingCombinations.values().iterator().next();
        } else {
            return null;
        }
    }

    private boolean couldBeSameTrackPart(TrackPart analyzedTrackPart, TrackPart currentTrackPart) {
        return analyzedTrackPart.getType() == currentTrackPart.getType();
    }


    @Override
    public void handlePenaltyMessage(PenaltyMessage message) {
        roundWithoutPenalties = false;

        ArrayList<TrackPart> lastMatchingTrackParts = findCurrentlyPassedCombination();

        if (notMatchedToExistingTrackparts(lastMatchingTrackParts)) {
            recordThisCombination();
            lastMatchingTrackParts = getLastTrackParts();
        }

        learnReducedSpeedForATrackPart(lastMatchingTrackParts);
    }

    private void learnReducedSpeedForATrackPart(ArrayList<TrackPart> lastMatchingTrackParts) {



        if (lastMatchingTrackParts.size() > 0) {
            int currentPositionIndex = COUNT_OF_TRACKPARTS_TO_COMPARE - 1 ;

            for (int i = currentPositionIndex; i >= 0; i--) {
                TrackPart trackPart = lastMatchingTrackParts.get(i);

                if (!learningMap.containsKey(trackPart.id)) {
                    learningMap.put(trackPart.id, BRAKE_POWER);
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
        if (roundWithoutPenalties) {

            fullPower = addToPower(fullPower, INCREASE_PER_SUCCESSFUL_ROUND, MAX_STRAIGHT_POWER);

            LOGGER.info("=> Increase Speed {}", fullPower);
        }

        roundWithoutPenalties = true;
    }

    @Override
    public void handleVelocityMessage(VelocityMessage message) {

        if (message.getVelocity() > maxMeasuredSpeed) {
            maxMeasuredSpeed = message.getVelocity();
        }

       if  (message.getVelocity() > maxMeasuredSpeed / 3 * 2 && currentSpeed < 100 - slowSpeed) {
            currentSpeed += slowSpeed / 2;
        } else if (message.getVelocity() < maxMeasuredSpeed / 3 && slowSpeed < 50) {
            slowSpeed += 5;
            currentSpeed = 0;
        }


    }

    @Override
    public void handleRoundTime(RoundTimeMessage message) {

        LOGGER.info("=> ROUND TIME {}", message.getRoundDuration());
    }

    @Override
    public int increase(int val) {
        currentPower = currentPower + val;
        return currentPower;
    }

    public int addToPower (int currentPower, int AddPower, int max) {
        int newPower = currentPower + AddPower;

        if (newPower > 0 && newPower <= max) {
            return newPower;
        } else if (newPower > 0 && newPower > max) {
            return max;
        } else {
            return currentPower;
        }
    }

    @Override
    public boolean iAmStillStanding() {
        return gzDiffHistory.currentStDev() < 3;
    }

    public boolean iAmReallySlow() {
        return currentSpeed < slowSpeed;
    }

    @Override
    public FloatingHistory getGzDiffHistory() {
        return gzDiffHistory;
    }

    public void updateCurrentSpeed(int currentPower) {

        if (currentSpeed >= 0 && currentPower < fullPower) {
            currentSpeed--;
        }

        if (currentSpeed <= 100 && currentPower >= fullPower) {
            currentSpeed++;
        }

    }
}
