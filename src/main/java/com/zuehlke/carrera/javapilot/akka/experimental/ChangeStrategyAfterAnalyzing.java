package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.*;
import com.zuehlke.carrera.javapilot.model.AnalyzedTrackPart;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.SmoothedSensorData;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.simulator.model.racetrack.TrackDesign;

@SuppressWarnings("Duplicates")
public class ChangeStrategyAfterAnalyzing extends UntypedActor {
    public static final int timestampDelayThreshold = 10;
    private final ActorRef pilotActor;
    private ActorRef trackPartRecognizer;
    private ActorRef trackAnalyzer;
    private PilotDataEventSender pilotDataEventSender;
    private double trackLength = 0.0;

    private long lastTimestamp = 0;
    private int currentPower = 20;

    private PowerStrategyInterface powerStrategy;

    private LowPassFilter lowPassFilter = new LowPassFilter(100);


    public static Props props(ActorRef pilotActor, PilotDataEventSender pilotDataEventSender) {
        return Props.create(
                ChangeStrategyAfterAnalyzing.class, () ->
                        new ChangeStrategyAfterAnalyzing(pilotActor, pilotDataEventSender));
    }

    public ChangeStrategyAfterAnalyzing(ActorRef pilotActor, PilotDataEventSender pilotDataEventSender) {
        this.pilotActor = pilotActor;
        this.pilotDataEventSender = pilotDataEventSender;
        this.trackPartRecognizer = getContext().system().actorOf(TrackPartRecognizer.props(getSelf()));
        this.trackAnalyzer = getContext().system().actorOf(TrackAnalyzer.props(getSelf()));
        powerStrategy = new ConstantPowerStrategy(pilotDataEventSender, pilotActor, getSelf());
        //powerStrategy = new HamiltonPowerStrategy(pilotDataEventSender, pilotActor, getSelf(), recognizedTrack);
    }

    @Override
    public void onReceive(Object message) throws Exception {

        if (message instanceof SensorEvent) {
            handleSensorEvent((SensorEvent) message, lastTimestamp, timestampDelayThreshold);
        } else if (message instanceof RaceStartMessage) {
            handleRaceStart();
        } else if (message instanceof TrackPartRecognizedEvent){
            handleTrackPartRecognized((TrackPartRecognizedEvent) message);
        }else if (message instanceof RoundTimeMessage) {
            powerStrategy.handleRoundTime((RoundTimeMessage) message);
        } else if ( message instanceof PenaltyMessage) {
            powerStrategy.handlePenaltyMessage ( (PenaltyMessage) message );
        }else if (message instanceof LapCompletedEvent) {
            powerStrategy.handleLapCompletedMessage((LapCompletedEvent) message);
        }else if(message instanceof LengthOfTrackComputedEvent){
            handleLengthOfTrackComputedEvent((LengthOfTrackComputedEvent) message);
        }
    }

    private void handleLengthOfTrackComputedEvent(LengthOfTrackComputedEvent message) {
        trackLength = message.getLength();
        trackPartRecognizer = getContext().system().actorOf(StdDevTrackPartRecognizer.props(getSelf(),trackLength));
        trackAnalyzer = getContext().system().actorOf(TrackAnalyzer.props(getSelf()));
        powerStrategy = new HamiltonPowerStrategy(pilotDataEventSender, pilotActor, getSelf());
    }

    public void handleSensorEvent(SensorEvent message, long lastTimestamp, long timestampDelayThreshold) {
        boolean obsoleteMessage = message.getTimeStamp() < lastTimestamp;

        if (obsoleteMessage) {
            return;
        }

        double gz = message.getG()[2];
        powerStrategy.getGzDiffHistory().shift(gz);

        double smoothValue = lowPassFilter.smoothen(gz, message.getTimeStamp());
        trackPartRecognizer.tell(new SmoothedSensorInputEvent(smoothValue, gz, message.getTimeStamp()), getSelf());

        SmoothedSensorData smoothedSensorData = new SmoothedSensorData(smoothValue, powerStrategy.getCurrentPower());
        pilotDataEventSender.sendToAll(smoothedSensorData);

        powerStrategy.handleSensorEvent(message, lastTimestamp, timestampDelayThreshold);
        this.lastTimestamp = message.getTimeStamp();
    }

    private void handleRaceStart() {
    }

    private void handleTrackPartRecognized(TrackPartRecognizedEvent event){
        powerStrategy.handleTrackPartRecognized(event);
        trackAnalyzer.tell(event,getSelf());
    }

}
