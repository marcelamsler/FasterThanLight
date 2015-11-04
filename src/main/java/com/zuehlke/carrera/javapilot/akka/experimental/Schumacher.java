package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.javapilot.akka.events.TrackAnalyzedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.timeseries.FloatingHistory;

public class Schumacher extends UntypedActor {
    public static final int timestampDelayThreshold = 10;
    private final ActorRef pilotActor;
    private final ActorRef trackPartRecognizer;
    private final ActorRef trackAnalyzer;
    private PilotDataEventSender pilotDataEventSender;
    private boolean trackRecognized = false;
    private int currentPower = 20;
    private final long maxRoundTime = 100000000;

    private int measuringPower = 200;
    private long lastTimestamp = 0;

    private PowerStrategyInterface powerStrategy;

    private Track<TrackPart> analyzedTrack = new Track<>();

    private LowPassFilter lowPassFilter = new LowPassFilter();

    private FloatingHistory gzDiffHistory = new FloatingHistory(4);

    public static Props props(ActorRef pilotActor, ActorRef trackRecognizer, ActorRef trackAnalyzer, PilotDataEventSender pilotDataEventSender) {
        return Props.create(
                Schumacher.class, () ->
                        new Schumacher(pilotActor,trackRecognizer, trackAnalyzer, pilotDataEventSender));
    }

    public Schumacher(ActorRef pilotActor, ActorRef trackPartRecognizer, ActorRef trackAnalyzer, PilotDataEventSender pilotDataEventSender) {
        this.pilotActor = pilotActor;
        this.pilotDataEventSender = pilotDataEventSender;
        this.trackPartRecognizer = trackPartRecognizer;
        this.trackAnalyzer = trackAnalyzer;

        powerStrategy = new ConstantPowerStrategy(pilotDataEventSender, pilotActor, lowPassFilter, trackPartRecognizer, getSelf(), gzDiffHistory, analyzedTrack);
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
            powerStrategy.handleTrackAnalyzed((TrackAnalyzedEvent) message);
        }
    }

    private void handlePenaltyMessage(PenaltyMessage message) {
    }

    private void handleRaceStart() {
        trackRecognized = true;
        trackAnalyzer.tell(new TrackRecognizedEvent(analyzedTrack),getSelf());
    }

    private void handleRoundTime(RoundTimeMessage message) {
        if(message.getRoundDuration() > maxRoundTime) {
            analyzedTrack = new Track<>();
        }
        else{
            trackRecognized = true;
            trackAnalyzer.tell(new TrackRecognizedEvent(analyzedTrack),getSelf());
        }
    }

}
