package com.zuehlke.carrera.javapilot.websocket;

import com.google.gson.Gson;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.relayapi.messages.VelocityMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Websocket handler, which is here to expos some events coming for the java pilot to the web client.
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
        sendMessage(velocityMessage);
    }

    public void sendToAll(SensorEvent message) {
        sendMessage(message);
    }

    private void sendMessage(Object genericMessage) {
        try {
            Gson gson = new Gson();
            String outputMessage = gson.toJson(genericMessage);

            LOGGER.info("Sending message to all {}", genericMessage.toString());
            for (WebSocketSession webSocketSession : sessionIdToOpenSession.values()) {
                webSocketSession.sendMessage(new TextMessage(outputMessage));
            }
        } catch (Exception e) {
            LOGGER.warn("Something went wrong - call chuck norris: ", e);
        }
    }
}
