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
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class VettelPowerStrategy implements PowerStrategyInterface {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HamiltonPowerStrategy.class);
    private static final int COUNT_OF_TRACKPARTS_TO_COMPARE = 60;
    private static final int STRAIGHT_POWER = 255;
    private static final int TURN_POWER = 150;
    private static final int COUNT_OF_FORWARD_LOOKING_TRACKPARTS = 6;
    private static final int BRAKE_POWER = 3;
    private static final double MAX_SPEED = 10.0;
    private static final int FAILURE_TOLERANCE_FOR_TRACKPART_MATCHING_IN_PERCENT = 20 ;
    private PilotDataEventSender pilotDataEventSender;

    private ActorRef pilotActor;
    private int defaultPower = 140;
    private int currentPower = defaultPower;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory;
    private Track<TrackPart> currentTrack = new Track<>();
    private HashMap<UUID, Integer> learningMap = new HashMap<>();
    private ArrayList<ArrayList<TrackPart>> recordedCombinations = new ArrayList<>();

    //TODO Calculate Gforce Limit
    private int gForceLimit = 10000;


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
        pilotDataEventSender.sendToAll(new CurrentProcessingTrackPart(myPosition));

        if (myPosition != null) {

            // speed is an number between 0 and 10
            int currentSpeed = estimateCurrentSpeed(passedCombination);

            currentPower = SetPowerAccordingToCurrentSpeedAndFutureTrackParts(passedCombination, currentSpeed);
            currentPower = getLearnedPowerIfAvailable(myPosition, currentSpeed, currentPower);
//            currentPower = getPowerFromActualGForce(message.getG()[2], currentPower);

            if (currentPower == -1) {
                currentPower = defaultPower;
            }
        } else {
            currentPower = defaultPower;
            recordThisCombination();
        }

        pilotActor.tell(new PowerAction(currentPower), sender);
    }

    private void recordThisCombination() {
        if (getLastTrackParts() != null && getLastTrackParts().size() == COUNT_OF_TRACKPARTS_TO_COMPARE) {
            recordedCombinations.add(getLastTrackParts());
        }
    }

    private int getPowerFromActualGForce(int gForce, int currentPower) {
        if (gForce > gForceLimit) {
            return 0;
        } else {
            return currentPower;
        }
    }

    private int SetPowerAccordingToCurrentSpeedAndFutureTrackParts(ArrayList<TrackPart> recordedCombination, int currentSpeed) {

        if (recordedCombination.size() == COUNT_OF_TRACKPARTS_TO_COMPARE + COUNT_OF_FORWARD_LOOKING_TRACKPARTS) {
            return estimatePossiblePower(recordedCombination, currentSpeed);
        } else {
            return -1;
        }
    }

    private int estimateCurrentSpeed(ArrayList<TrackPart> recordedCombination) {
        int speed = 0;
        double speedChangePerTrackPart = MAX_SPEED / COUNT_OF_TRACKPARTS_TO_COMPARE;

        for (int i = 0; i < recordedCombination.size() - COUNT_OF_FORWARD_LOOKING_TRACKPARTS; i++) {
            if (recordedCombination.get(i).getType() == TrackType.STRAIGHT) {
                speed += speedChangePerTrackPart;
            } else {
                speed -= speedChangePerTrackPart;
            }
        }

        if ( iAmReallySlow()) {
            speed -= 5;
        }

        return speed;
    }


    private int estimatePossiblePower(ArrayList<TrackPart> recordedCombination, int currentSpeed) {
        TrackType nextTrackPartType = recordedCombination.get(recordedCombination.size() - 1).getType();

        boolean straightAhead = nextTrackPartType == TrackType.STRAIGHT;
        boolean turnAhead = (nextTrackPartType == TrackType.LEFT || nextTrackPartType == TrackType.RIGHT);


        if (straightAhead) {
            return STRAIGHT_POWER;
        } else if (turnAhead) {
            if (currentSpeed > 5) {
                return BRAKE_POWER;
            } else {
                return TURN_POWER;
            }
        } else {
            return defaultPower;
        }

    }

    private int getLearnedPowerIfAvailable(TrackPart myPosition,int currentSpeed, int currentPower) {
        if (learningMap.containsKey(myPosition.id)) {
            return learningMap.get(myPosition.id);
        } else {
            return currentPower;
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
        LOGGER.info("=> Handle penalty {}", message.toString());

        ArrayList<TrackPart> lastMatchingTrackParts = findCurrentlyPassedCombination();

        if (notMatchedToExistingTrackparts(lastMatchingTrackParts)) {
            recordThisCombination();
            lastMatchingTrackParts = getLastTrackParts();
        } else {
            LOGGER.info("=> found existing trackparts {}", message.toString());
        }

        learnReducedSpeedForATrackPart(lastMatchingTrackParts);
    }

    private void learnReducedSpeedForATrackPart(ArrayList<TrackPart> lastMatchingTrackParts) {

        pilotDataEventSender.sendToAll(new CurrentProcessingTrackPart(lastMatchingTrackParts.get(lastMatchingTrackParts.size() - 1)));

        if (lastMatchingTrackParts.size() > 0) {
            int currentPositionIndex = lastMatchingTrackParts.size() - (1 + COUNT_OF_FORWARD_LOOKING_TRACKPARTS);

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

    }

    @Override
    public void handleRoundTime(RoundTimeMessage message) {

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

    public boolean iAmReallySlow() { return gzDiffHistory.currentStDev() < 5; }

    @Override
    public FloatingHistory getGzDiffHistory() {
        return gzDiffHistory;
    }
}
