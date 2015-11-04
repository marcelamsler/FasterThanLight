package com.zuehlke.carrera.javapilot.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.zuehlke.carrera.javapilot.akka.experimental.ConstantPowerAnalyzer;
import com.zuehlke.carrera.javapilot.akka.experimental.Schumacher;
import com.zuehlke.carrera.javapilot.akka.experimental.TrackAnalyzer;
import com.zuehlke.carrera.javapilot.akka.experimental.TrackPartRecognizer;
import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;

import java.util.HashMap;
import java.util.Map;

/**
 *      creates the complete topology and provides a Map of well-defined entry-points
 */
public class PilotTopology {

    public static final String SENSOR_ENTRYPOINT = "SENSOR_ENTRYPOINT";
    public static final String VELOCITY_ENTRYPOINT = "VELOCITY_ENTRYPOINT";
    public static final String PENALTY_ENTRYPOINT = "PENALTY_ENTRYPOINT";

    private final ActorSystem system;
    private final ActorRef kobayashi;
    private final Map<String, ActorRef> entryPoints = new HashMap<>();

    public PilotTopology(ActorRef kobayashi, ActorSystem system) {
        this.kobayashi = kobayashi;
        this.system = system;
    }

    public Map<String, ActorRef> create(PilotDataEventSender pilotDataEventSender) {
        ActorRef trackPartRecognizer = system.actorOf(TrackPartRecognizer.props());
        ActorRef trackAnalyzer = system.actorOf(TrackAnalyzer.props());
        ActorRef initialProcessor = system.actorOf(Schumacher.props(kobayashi, trackPartRecognizer,trackAnalyzer, pilotDataEventSender));

        entryPoints.put(PENALTY_ENTRYPOINT, initialProcessor);
        entryPoints.put(SENSOR_ENTRYPOINT, initialProcessor);
        entryPoints.put(VELOCITY_ENTRYPOINT, initialProcessor);

        return entryPoints;
    }

}
