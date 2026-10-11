package com.taskmanagement.demo.ioc;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component 
@Primary
public class CasualGreeter implements Greeter {
    private boolean initializedBySpring;
    private boolean destroyedBySpring;

    @PostConstruct
    void onSpringInitialization() {
        initializedBySpring = true;
    }

    boolean isInitializedBySpring() {
        return initializedBySpring;
    }

    @PreDestroy
    void onSpringDestruction() {
        destroyedBySpring = true;
    }

    boolean isDestroyedBySpring() {
        return destroyedBySpring;
    }

    @Override 
    public String greet(String name) {
        return "Chào " + name + " nhé!";
    }
}
