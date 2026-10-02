package com.taskmanagement.demo.ioc;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component 
@Primary
public class CasualGreeter implements Greeter {
    @Override 
    public String greet(String name) {
        return "Chào" + name + "nhé!";
    }
}
