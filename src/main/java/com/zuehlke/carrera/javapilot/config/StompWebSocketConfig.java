package com.zuehlke.carrera.javapilot.config;

import com.zuehlke.carrera.javapilot.websocket.PilotDataEventSender;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;

import java.util.concurrent.Executors;

@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocket
/**
 * We need this annotations, since there are somehow to scheduler configured.
 * With thid annotations + SchedulingConfigurer implementation we change this to one scheduler.
 */
@EnableScheduling
class StompWebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer implements WebSocketConfigurer, SchedulingConfigurer {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StompWebSocketConfig.class);

    public void configureMessageBroker ( MessageBrokerRegistry config ) {
        config.enableSimpleBroker("/topic");

        /**
         * The given channel-prefixes are used to filter out incoming
         * SEND commands. Only those matching this filter will be handed over to the
         * Controllers for further processing.
         *
         * A client should therefore send a message to a destination channel like:
         *
         * channel: /app/echo
         *
         * Where '/echo' is the actual mapping in the controllers.
         *
         * */
        config.setApplicationDestinationPrefixes("/app"); // The client has
        LOGGER.info("=> configureSimpleBroker");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        // Register a pure WebSocket endpoint (ws://myserver.com/ws/rest/messages)
        registry.addEndpoint("/messages");

        // Register additional fallback handling using sock-js (http://myserver.com/ws/rest/messages)
        registry.addEndpoint("/messages").withSockJS();
    }

    /**
     * Configure plain WS handler here
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
        LOGGER.info("=> register pilotDataEventSender to '/pilotData' ");
        webSocketHandlerRegistry.addHandler(pilotDataEventSender(), "/pilotData");
    }

    @Bean
    public PilotDataEventSender pilotDataEventSender() {
        return new PilotDataEventSender();
    }

    /**
     * The code is inspired from: https://github.com/ralscha/spring4ws-demos/blob/master/src/main/java/ch/rasc/s4ws/Spring4WebSocketExamplesApplication.java
     * Because of this bug:
     * https://jira.spring.io/browse/SPR-11498
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar scheduledTaskRegistrar) {
        scheduledTaskRegistrar.setTaskScheduler(new ConcurrentTaskScheduler(
                Executors.newSingleThreadScheduledExecutor()));
    }
}
