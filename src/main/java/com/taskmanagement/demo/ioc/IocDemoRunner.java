package com.taskmanagement.demo.ioc;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class IocDemoRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(IocDemoRunner.class);

    private final GreetingService greetingService;
    private final ReminderService reminderService;
    private final List<Greeter> allGreeters;

    public IocDemoRunner(GreetingService greetingService, ReminderService reminderService, List<Greeter> allGreeters) {
        this.greetingService = greetingService;
        this.reminderService = reminderService;
        this.allGreeters = allGreeters;
    }

    @Override 
    public void run(String... arg) {
        log.info("==================== IoC / DI DEMO ====================");
 
        log.info("Container tìm thấy {} bean implement Greeter:", allGreeters.size());
        allGreeters.forEach(g -> log.info("  - {}", g.getClass().getSimpleName()));
 
        log.info("GreetingService.buildGreeting(\"Phuc\") -> {}",
                greetingService.buildGreeting("Phuc"));
        log.info("  => CasualGreeter được đánh dấu @Primary nên container tự chọn nó"
                + " để tiêm vào GreetingService, dù class này chỉ khai báo phụ thuộc"
                + " vào interface Greeter, không hề biết tới CasualGreeter.");
 
        Greeter injectedIntoGreetingService = greetingService.exposeGreeter();
        Greeter injectedIntoReminderService = reminderService.exposeGreeter();
        boolean sameInstance = injectedIntoGreetingService == injectedIntoReminderService;
 
        log.info("Instance Greeter trong GreetingService và ReminderService có phải"
                + " CÙNG MỘT OBJECT không? {}", sameInstance);
        log.info("  identityHashCode trong GreetingService: {}",
                System.identityHashCode(injectedIntoGreetingService));
        log.info("  identityHashCode trong ReminderService: {}",
                System.identityHashCode(injectedIntoReminderService));
        log.info("  => {} vì bean Greeter mặc định có scope \"singleton\": container chỉ"
                + " tạo DUY NHẤT 1 instance CasualGreeter rồi tái sử dụng ở mọi nơi"
                + " cần tới nó, kể cả khi hai service hoàn toàn không biết nhau.",
                sameInstance ? "Giống nhau, như kỳ vọng" : "Khác nhau (không mong đợi!)");

        CasualGreeter managed = (CasualGreeter) injectedIntoGreetingService;
        CasualGreeter manuallyCreated = new CasualGreeter();
        log.info("Spring tạo bean khi khởi tạo ApplicationContext; @PostConstruct được gọi: {}",
                managed.isInitializedBySpring());
        log.info("Tự new() tạo instance ngoài container: cùng object với bean? {};"
                + " @PostConstruct được gọi? {}",
                manuallyCreated == managed, manuallyCreated.isInitializedBySpring());
        log.info("Instance tự new() không được Spring tiêm dependency, quản lý vòng đời"
                + " hoặc áp dụng proxy cho @Transactional/@Cacheable.");
 
        log.info("=========================================================");
    }
}
