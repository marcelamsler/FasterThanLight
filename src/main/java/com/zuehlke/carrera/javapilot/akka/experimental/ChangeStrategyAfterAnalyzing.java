package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.javapilot.akka.events.TrackAnalyzedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.AnalyzedTrackPart;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.simulator.model.racetrack.TrackDesign;

@SuppressWarnings("Duplicates")
public class ChangeStrategyAfterAnalyzing extends UntypedActor {
    public static final int timestampDelayThreshold = 10;
    private final ActorRef pilotActor;
    private final ActorRef trackPartRecognizer;
    private final ActorRef trackAnalyzer;
    private PilotDataEventSender pilotDataEventSender;
    private boolean trackRecognized = false;
    private int currentPower = 20;
    private final long maxRoundTime = 100000000;
    private boolean firstRound  = true;

    private int measuringPower = 200;
    private long lastTimestamp = 0;

    private PowerStrategyInterface powerStrategy;

    private Track<TrackPart> recognizedTrack = new Track<>();
    private Track<AnalyzedTrackPart> analyzedTrack = new Track<>();

    private LowPassFilter lowPassFilter = new LowPassFilter();

    public static Props props(ActorRef pilotActor, PilotDataEventSender pilotDataEventSender) {
        return Props.create(
                ChangeStrategyAfterAnalyzing.class, () ->
                        new ChangeStrategyAfterAnalyzing(pilotActor, pilotDataEventSender));
    }

    public ChangeStrategyAfterAnalyzing(ActorRef pilotActor, PilotDataEventSender pilotDataEventSender) {
        this.pilotActor = pilotActor;
        this.pilotDataEventSender = pilotDataEventSender;
        this.trackPartRecognizer = getContext().system().actorOf(TrackPartRecognizer.props(getSelf()));;
        this.trackAnalyzer = getContext().system().actorOf(TrackAnalyzer.props(getSelf()));

        powerStrategy = new ConstantPowerStrategy(pilotDataEventSender, pilotActor, lowPassFilter, trackPartRecognizer, getSelf(), recognizedTrack);
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof SensorEvent) {
            powerStrategy.handleSensorEvent((SensorEvent) message, lastTimestamp, timestampDelayThreshold);
        } else if (message instanceof RaceStartMessage) {
            handleRaceStart();
        } else if (message instanceof TrackPartRecognizedEvent){
            powerStrategy.handleTrackPartRecognized((TrackPartRecognizedEvent) message);
        }else if (message instanceof RoundTimeMessage) {
            handleRoundTime((RoundTimeMessage) message);
        } else if ( message instanceof PenaltyMessage) {
            handlePenaltyMessage ( (PenaltyMessage) message );
        }else if (message instanceof TrackAnalyzedEvent){
            TrackAnalyzedEvent trackAnalyzedEvent = (TrackAnalyzedEvent) message;
            analyzedTrack = trackAnalyzedEvent.getTrack();
            handleTrackAnalyzed(trackAnalyzedEvent);
        }
    }

    public void handleTrackAnalyzed(final TrackAnalyzedEvent message) {
        Track<AnalyzedTrackPart> analyzedTrack = message.getTrack();
        TrackDesign trackDesign = convertTrackForWebsocket(analyzedTrack);
        pilotDataEventSender.sendToAll(trackDesign);
    }

    private void handlePenaltyMessage(PenaltyMessage message) {
    }

    private void handleRaceStart() {
    }

    private void handleRoundTime(RoundTimeMessage message) {
        if(message.getRoundDuration() > maxRoundTime) {
            recognizedTrack = new Track<>();
        }
        else{
            if(firstRound){
                firstRound = false;
            }
            else {
                trackRecognized = true;
                trackAnalyzer.tell(new TrackRecognizedEvent(recognizedTrack), getSelf());
            }
        }
    }

    private TrackDesign convertTrackForWebsocket(Track<AnalyzedTrackPart> track) {
        TrackDesign trackDesign = new TrackDesign();

        for(AnalyzedTrackPart analyzedTrackPart: track.getTrackParts()){
            if (analyzedTrackPart.isStraight()){
                trackDesign.straight(analyzedTrackPart.getLength());
            }
            else if(analyzedTrackPart.isCurve()){
                trackDesign.curve(analyzedTrackPart.getRadius(),analyzedTrackPart.getAngle());
            }
        }
        trackDesign.create();
        return trackDesign;
    }

}
