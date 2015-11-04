package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SchumacherPowerStrategy implements PowerStrategyInterface{

    public static final int COUNT_OF_TRACKPARTS_TO_COMPARE = 3;
    private static final double REDUCE_SPEED_RATIO_AFTER_PENALTY = 0.95;

    private PilotDataEventSender pilotDataEventSender;
    private ActorRef pilotActor;
    private int defaultPower = 200;
    private int defaultPowerBeforePenalty = 180;
    private int currentPower = defaultPower;
    private ActorRef sender;
    private FloatingHistory gzDiffHistory;
    private Track<TrackPart> analyzedTrack;
    private Track<TrackPart> currentTrack = new Track<>();
    private HashMap<UUID, Integer> learningMap = new HashMap<>();


    public SchumacherPowerStrategy(PilotDataEventSender pilotDataEventSender, ActorRef pilotActor, ActorRef sender, Track<TrackPart> analyzedTrack) {
        this.pilotDataEventSender = pilotDataEventSender;
        this.pilotActor = pilotActor;
        this.sender = sender;
        this.gzDiffHistory = new FloatingHistory(4);
        this.analyzedTrack = analyzedTrack;
    }

    @Override
    public void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        currentTrack.addTrackPart(message.getPart());
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize()));
    }

    @Override
    public void handleSensorEvent(SensorEvent message, long lastTimestamp, long timestampDelayThreshold) {
        TrackPart lastTrackPart = findCurrentPositionInAnalyzedTrack();

        if (lastTrackPart != null) {
            if (learningMap.containsKey(lastTrackPart.id)) {
                currentPower = learningMap.get(lastTrackPart.id);
            }
        }

        pilotActor.tell(new PowerAction(currentPower), sender);
    }

    private TrackPart findCurrentPositionInAnalyzedTrack() {
        ArrayList<TrackPart> lastMatchingTrackParts = findTrackPartsInAnalyzedTrack(currentTrack.getLastTrackParts(COUNT_OF_TRACKPARTS_TO_COMPARE));

        if (lastMatchingTrackParts != null && !lastMatchingTrackParts.isEmpty()) {
            return lastMatchingTrackParts.get(lastMatchingTrackParts.size() - 1);
        } else {
            return null;
        }

    }

    private ArrayList<TrackPart> findTrackPartsInAnalyzedTrack(ArrayList<TrackPart> currentTrackParts) {
        ArrayList<TrackPart> analyzedTrackParts = analyzedTrack.getTrackParts();
        for(TrackPart analyzedTrackPart : analyzedTrackParts) {
            boolean patternMatches = true;
            for(TrackPart currentTrackPart : currentTrackParts ) {
                if (!couldBeSameTrackPart(analyzedTrackPart, currentTrackPart)) {
                    patternMatches = false;
                    break;
                }
            }
            if (patternMatches) {
                int indexOfCurrentTrackPart = analyzedTrackParts.indexOf(analyzedTrackPart);
                int indexOfLastWantedTrackPart = indexOfCurrentTrackPart + currentTrackParts.size();

                if (indexOfLastWantedTrackPart > analyzedTrackParts.size() - 1) {
                    indexOfLastWantedTrackPart = analyzedTrackParts.size() - 1;
                }

                return new ArrayList<>(analyzedTrackParts.subList(indexOfCurrentTrackPart, indexOfLastWantedTrackPart));
            }

        }
        return null;
    }

    private boolean couldBeSameTrackPart(TrackPart analyzedTrackPart, TrackPart currentTrackPart) {
        return analyzedTrackPart.getType() == currentTrackPart.getType() &&
                hasAboutSameSize(analyzedTrackPart.getSize(), currentTrackPart.getSize());
    }

    private boolean hasAboutSameSize(int constantPowerDuration, int racePowerDuration) {
        double maxSlowerRatio = 0.5;
        double maxFasterRatio = 3.0;
        return racePowerDuration > constantPowerDuration * maxSlowerRatio &&
            racePowerDuration < constantPowerDuration * maxFasterRatio;
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

    @Override
    public FloatingHistory getGzDiffHistory() {
        return gzDiffHistory;
    }

    @Override
    public void handlePenaltyMessage(PenaltyMessage message) {
        ArrayList<TrackPart> lastMatchingTrackParts = findTrackPartsInAnalyzedTrack(currentTrack.getLastTrackParts(COUNT_OF_TRACKPARTS_TO_COMPARE));

        TrackPart beforePenaltyTrackPart = null;
        if (lastMatchingTrackParts != null) {
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
}
