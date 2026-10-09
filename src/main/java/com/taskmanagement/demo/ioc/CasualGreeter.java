package com.taskmanagement.demo.ioc;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component 
@Primary
public class CasualGreeter implements Greeter {
    private boolean initializedBySpring;

    @PostConstruct
    void onSpringInitialization() {
        initializedBySpring = true;
    }

    boolean isInitializedBySpring() {
        return initializedBySpring;
    }

    @Override 
    public String greet(String name) {
        return "Chào " + name + " nhé!";
    }
}
