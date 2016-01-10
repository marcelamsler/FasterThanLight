package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.LapCompletedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.relayapi.messages.VelocityMessage;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SchumacherPowerStrategy implements PowerStrategyInterface{

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HamiltonPowerStrategy.class);
    private static final int COUNT_OF_TRACKPARTS_TO_COMPARE = 5;
    private static final double REDUCE_SPEED_RATIO_AFTER_PENALTY = 0.95;
    private static final double MAX_SLOWER_RATIO_OF_RACE_TRACKPART = 0.5;
    private static final double MAX_FASTER_RATIO_OF_RACE_TRACKPART = 5.0;

    private PilotDataEventSender pilotDataEventSender;
    private ActorRef pilotActor;
    private int defaultPower = 256;
    private int defaultPowerBeforePenalty = 180;
    private int currentPower = defaultPower;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory;
    private Track<TrackPart> analyzedTrack;
    private Track<TrackPart> currentTrack = new Track<>();
    private HashMap<UUID, Integer> learningMap = new HashMap<>();
    boolean roundWithoutPenalties;


    public SchumacherPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, ActorRef sender, Track<TrackPart> analyzedTrack) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(4);
        this.analyzedTrack = analyzedTrack;
        analyzedTrack.getTrackParts().addAll(analyzedTrack.getTrackParts().subList(0, COUNT_OF_TRACKPARTS_TO_COMPARE));
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        currentTrack.addTrackPart(message.getPart());
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize(), message.getPart().id.toString()));
    }

    @Override
    public void handleSensorEvent(SensorEvent message, long lastTimestamp, long timestampDelayThreshold) {
        TrackPart lastTrackPart = findCurrentPositionInAnalyzedTrack();

        if (lastTrackPart != null) {
            if (learningMap.containsKey(lastTrackPart.id)) {
                currentPower = learningMap.get(lastTrackPart.id);
            } else {
                currentPower = defaultPower;
            }
        }

        pilotActor.tell(new PowerAction(currentPower), sender);
    }

    private TrackPart findCurrentPositionInAnalyzedTrack() {
        ArrayList<TrackPart> lastTrackParts = currentTrack.getLastTrackParts(COUNT_OF_TRACKPARTS_TO_COMPARE);

        ArrayList<TrackPart> lastMatchingTrackParts = null;
        if (lastTrackParts.size() == COUNT_OF_TRACKPARTS_TO_COMPARE) {
            lastMatchingTrackParts = findTrackPartsInAnalyzedTrack(lastTrackParts);
        }


        if (lastMatchingTrackParts != null && !lastMatchingTrackParts.isEmpty()) {
            return lastMatchingTrackParts.get(lastMatchingTrackParts.size() - 1);
        } else {
            return null;
        }

    }

    private ArrayList<TrackPart> findTrackPartsInAnalyzedTrack(ArrayList<TrackPart> currentTrackParts) {
        ArrayList<TrackPart> analyzedTrackParts = analyzedTrack.getTrackParts();


        for(int i = 0; i < analyzedTrackParts.size(); i++) {
            boolean patternMatches = true;
            for (int j = 0; j < currentTrackParts.size(); j++) {
                if (i + j < analyzedTrackParts.size() && !couldBeSameTrackPart(analyzedTrackParts.get(i + j), currentTrackParts.get(j))) {
                    patternMatches = false;
                    break;
                }
            }

            if (patternMatches) {
                LOGGER.info("=> found matching pattern of trackparts");
                int indexOfLastWantedTrackPart = i + currentTrackParts.size();

                if (indexOfLastWantedTrackPart > analyzedTrackParts.size()) {
                    indexOfLastWantedTrackPart = analyzedTrackParts.size();
                }
                return new ArrayList<>(analyzedTrackParts.subList(i, indexOfLastWantedTrackPart));
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
        ArrayList<TrackPart> lastMatchingTrackParts = findTrackPartsInAnalyzedTrack(currentTrack.getLastTrackParts(COUNT_OF_TRACKPARTS_TO_COMPARE));

        TrackPart beforePenaltyTrackPart = null;
        if (lastMatchingTrackParts != null && !lastMatchingTrackParts.isEmpty()) {
            beforePenaltyTrackPart = lastMatchingTrackParts.get(lastMatchingTrackParts.size() - 1);
        }

        if(beforePenaltyTrackPart != null) {
            UUID trackPartId = beforePenaltyTrackPart.id;
            if (learningMap.containsKey(trackPartId)) {
                int reducedPowerValue = (int) Math.round(learningMap.get(trackPartId) * REDUCE_SPEED_RATIO_AFTER_PENALTY);
                learningMap.put(trackPartId, reducedPowerValue);
            } else {
                learningMap.put(beforePenaltyTrackPart.id, defaultPowerBeforePenalty);
            }
        }
    }

    @Override
    public void handleRoundTime(RoundTimeMessage message) {

        if (roundWithoutPenalties) {
            for(int value : learningMap.values()) {
                value += 5;
            }
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

    @Override
    public int getCurrentPower() {
        return currentPower;
    }

    @Override
    public void handleLapCompletedMessage(LapCompletedEvent message) {

    }

    @Override
    public void handleVelocityMessage(VelocityMessage message) {

    }
}
