package com.taskmanagement.demo.ioc;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PrototypeNote {
    private boolean initializedBySpring;
    private boolean destroyedBySpring;

    @PostConstruct
    void onSpringInitialization() {
        initializedBySpring = true;
    }

    @PreDestroy
    void onSpringDestruction() {
        destroyedBySpring = true;
    }

    boolean isInitializedBySpring() {
        return initializedBySpring;
    }

    boolean isDestroyedBySpring() {
        return destroyedBySpring;
    }
}
