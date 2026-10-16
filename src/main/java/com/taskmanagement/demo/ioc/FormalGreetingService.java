package com.taskmanagement.demo.ioc;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FormalGreetingService {
    private final Greeter greeter;

    public FormalGreetingService(@Qualifier("formalgreeter") Greeter greeter) {
        this.greeter = greeter;
    }

    public String buildGreeting(String name) {
        return greeter.greet(name);
    }

    Greeter exposeGreeter() {
        return greeter;
    }
}
