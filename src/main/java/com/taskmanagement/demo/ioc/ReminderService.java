package com.taskmanagement.demo.ioc;
import org.springframework.stereotype.Service;

@Service 
public class ReminderService {
    private final Greeter greeter;

    public ReminderService(Greeter greeter) {
        this.greeter = greeter;
    }

    String buildReminder(String name) {
        return greeter.greet(name) + "Đừng quên kiểm tra task hôm nay.";
    }

    //demo
    Greeter exposeGreeter() {
        return greeter;
    }
}
