package com.claubloom.harness.server.config;

import com.claubloom.harness.server.PiServer;
import com.claubloom.harness.server.service.PiServerService;
import com.claubloom.harness.server.sessions.LiveSessionManager;
import com.claubloom.harness.server.stream.DefaultSessionEventBroadcaster;
import com.claubloom.harness.server.stream.SessionEventBroadcaster;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot AutoConfiguration for BloomHarness Server module.
 */
@AutoConfiguration
@EnableConfigurationProperties(ServerProperties.class)
public class ServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SessionEventBroadcaster sessionEventBroadcaster() {
        return new DefaultSessionEventBroadcaster();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public PiServer piServer(PiServerService service, ServerProperties properties) {
        return new PiServer(
                properties.getServerId(),
                properties.getMaxFrameLength(),
                properties.getHandshakeTimeoutMs(),
                error -> {},
                service);
    }

    @Bean
    @ConditionalOnMissingBean
    public LiveSessionManager liveSessionManager(PiServer piServer) {
        return piServer.getSessions();
    }
}
