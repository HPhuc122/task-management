package com.taskmanagement.demo.ioc;
import org.springframework.stereotype.Component;

@Component 
public class Formaigreeter implements Greeter {
    @Override 
    public String greet(String name){
        return "Khính chào " + name + ".";
    }
}
