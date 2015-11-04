package com.zuehlke.carrera.javapilot.akka.experimental;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.javapilot.akka.PowerAction;
import com.zuehlke.carrera.javapilot.akka.events.SmoothedSensorInputEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackAnalyzedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackPartRecognizedEvent;
import com.zuehlke.carrera.javapilot.akka.events.TrackRecognizedEvent;
import com.zuehlke.carrera.javapilot.model.AnalyzedTrackPart;
import com.zuehlke.carrera.javapilot.model.Track;
import com.zuehlke.carrera.javapilot.model.TrackPart;
import com.zuehlke.carrera.javapilot.services.LowPassFilter;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import com.zuehlke.carrera.javapilot.websocket.data.SmoothedSensorData;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.simulator.model.racetrack.TrackDesign;
import com.zuehlke.carrera.timeseries.FloatingHistory;

public class ConstantPowerAnalyzer extends UntypedActor {
    public static final int timestampDelayThreshold = 10;
    private final ActorRef pilotActor;
    private final ActorRef trackPartRecognizer;
    private final ActorRef trackAnalyzer;
    private PilotDataEventSender pilotDataEventSender;
    private boolean trackRecognized = false;
    private int currentPower = 20;
    private final long maxRoundTime = 100000000;
    private boolean firstRound = true;

    private int measuringPower = 110;
    private long lastTimestamp = 0;


    private Track<TrackPart> track = new Track<>();

    private LowPassFilter lowPassFilter = new LowPassFilter();

    private FloatingHistory gzDiffHistory = new FloatingHistory(4);

    public static Props props(ActorRef pilotActor, PilotDataEventSender pilotDataEventSender) {
        return Props.create(
                ConstantPowerAnalyzer.class, () ->
                        new ConstantPowerAnalyzer(pilotActor, pilotDataEventSender));
    }

    public ConstantPowerAnalyzer(ActorRef pilotActor,PilotDataEventSender pilotDataEventSender) {
        this.pilotActor = pilotActor;
        this.pilotDataEventSender = pilotDataEventSender;
        this.trackPartRecognizer = getContext().system().actorOf(TrackPartRecognizer.props(getSelf()));
        this.trackAnalyzer =  getContext().system().actorOf(TrackAnalyzer.props(getSelf()));
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof SensorEvent) {
            handleSensorEvent((SensorEvent) message);
        } else if (message instanceof RaceStartMessage) {
            handleRaceStart();
        } else if (message instanceof TrackPartRecognizedEvent){
            handleTrackPartRecognized((TrackPartRecognizedEvent) message);
        }else if (message instanceof RoundTimeMessage) {
            handleRoundTime((RoundTimeMessage) message);
        }else if (message instanceof TrackAnalyzedEvent){
            handleTrackAnalyzed((TrackAnalyzedEvent) message);
        }
    }

    private void handleTrackAnalyzed(TrackAnalyzedEvent message) {
        Track<AnalyzedTrackPart> track = message.getTrack();
        TrackDesign trackDesign = convertTrackForWebsocket(track);
        pilotDataEventSender.sendToAll(trackDesign);
    }

    private void handleRoundTime(RoundTimeMessage message) {
        if(message.getRoundDuration() > maxRoundTime) {
            track = new Track<>();
        }
        else{
            if(firstRound){
                firstRound = false;
            }
            else {
                trackRecognized = true;
                trackAnalyzer.tell(new TrackRecognizedEvent(track), getSelf());
            }
        }
    }

    private void handleTrackPartRecognized(TrackPartRecognizedEvent message) {
        if(!firstRound) {
            track.addTrackPart(message.getPart());
        }
        System.out.println(message.getPart().getDuration());
        pilotDataEventSender.sendToAll(new TrackPartChangedData(message.getPart().getType(), message.getPart().getSize()));
    }

    private void handleRaceStart() {
        trackRecognized = true;
        trackAnalyzer.tell(new TrackRecognizedEvent(track),getSelf());
    }

    private void handleSensorEvent(SensorEvent message) {
        /**
         * Is the case when you get a messageA and then a messageB.
         * You've processed messageB - missed messageA. Now you process messageC.
         * And messageA comes again - then you ignore that
         */
        boolean obsoleteMessage = message.getTimeStamp() < lastTimestamp;

        /**
         * If for long time no message comes
         */
        boolean noMessageForFewMillies = System.currentTimeMillis() - message.getTimeStamp() > timestampDelayThreshold;

        if (obsoleteMessage /*|| noMessageForFewMillies*/) {
            return;
        }
        double gz = message.getG()[2];
        gzDiffHistory.shift(gz);


        double smoothValue = lowPassFilter.smoothen(gz, message.getTimeStamp());
        trackPartRecognizer.tell(new SmoothedSensorInputEvent(smoothValue, gz), getSelf());
        SmoothedSensorData smoothedSensorData = new SmoothedSensorData(smoothValue, currentPower);
        pilotDataEventSender.sendToAll(smoothedSensorData);

        if (iAmStillStanding()) {
            increase(5);
            pilotActor.tell(new PowerAction(currentPower), getSelf());
        } else {
            currentPower = measuringPower;
            pilotActor.tell(new PowerAction(currentPower), getSelf());
        }
    }

    private int increase(int val) {
        currentPower = currentPower + val;
        return currentPower;
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

    private boolean iAmStillStanding() {
        return gzDiffHistory.currentStDev() < 3;
    }

}
