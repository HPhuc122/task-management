package com.taskmanagement.demo.ioc;
import org.springframework.stereotype.Service;

@Service 
public class GreetingService {
    private final Greeter greeter;

    public GreetingService(Greeter greeter){
        this.greeter = greeter;
    }

    public String buildGreeting(String name) {
        return greeter.greet(name);
    }

    //demo
    Greeter exposeGreeter(){
        return greeter;
    }
}
