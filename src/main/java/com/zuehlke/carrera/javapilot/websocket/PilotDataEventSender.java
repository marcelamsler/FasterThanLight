package com.zuehlke.carrera.javapilot.websocket;

import com.google.gson.Gson;
import com.zuehlke.carrera.javapilot.websocket.data.EventMessageType;
import com.zuehlke.carrera.javapilot.websocket.data.SmoothedSensorData;
import com.zuehlke.carrera.javapilot.websocket.data.TrackPartChangedData;
import com.zuehlke.carrera.relayapi.messages.RoundTimeMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.relayapi.messages.VelocityMessage;
import com.zuehlke.carrera.simulator.model.racetrack.TrackDesign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Websocket handler, which is here to expose some events coming for the java pilot to the web client.
 *
 * Used with plain web socket, no STOMP wrapping.
 *
 * Example ( used for debugging ) :
 * var ws = new WebSocket("ws://localhost:8089/pilotData")
 * ws.onmessage = function(e){
 *  console.log(e)
 * }
 *
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class PilotDataEventSender extends TextWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PilotDataEventSender.class);

    private Map<String, WebSocketSession> sessionIdToOpenSession = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        LOGGER.info("Connection established with {}", session.getId());
        sessionIdToOpenSession.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);

        LOGGER.info("Connection closed with {}", session.getId());
        sessionIdToOpenSession.remove(session.getId());
    }

    public void sendToAll(VelocityMessage velocityMessage) {
        sendMessage(velocityMessage, EventMessageType.VelocityMessage);
    }

    public void sendToAll(SmoothedSensorData smoothedSensorData) {
        sendMessage(smoothedSensorData, EventMessageType.SmoothedSensorData);
    }

    public void sendToAll(SensorEvent message) {
        sendMessage(message, EventMessageType.SensorEvent);
    }

    public void sendToAll(TrackPartChangedData trackPartChangedData) {
        sendMessage(trackPartChangedData, EventMessageType.TrackPartChanged);
    }

    private void sendMessage(Object genericMessage, EventMessageType eventMessageType) {
        try {
            Gson gson = new Gson();

            JsonMessage jsonMessage =
                    new JsonMessage(genericMessage, eventMessageType);
            String outputMessage = gson.toJson(jsonMessage);

            LOGGER.debug("Sending message to all {}", outputMessage);
            for (WebSocketSession webSocketSession : sessionIdToOpenSession.values()) {
                webSocketSession.sendMessage(new TextMessage(outputMessage));
            }
        } catch (Exception e) {
            LOGGER.warn("Something went wrong - call chuck norris: ");
        }
    }

    public void sendToAll(RoundTimeMessage message) {
        sendMessage(message,EventMessageType.LapCompleted);
    }

    public void sendToAll(TrackDesign trackDesign){
        sendMessage(trackDesign,EventMessageType.trackInfo);
    }
}
