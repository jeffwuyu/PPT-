package com.pptagent.agent.progress;

import org.springframework.stereotype.Component;

@Component
public class NoopAgentProgressPublisher implements AgentProgressPublisher {

    @Override
    public void publish(AgentProgressEvent event) {
        // Default publisher for deployments that do not expose live progress streams.
    }
}
