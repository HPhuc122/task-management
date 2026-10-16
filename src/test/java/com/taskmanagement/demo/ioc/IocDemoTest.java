package com.taskmanagement.demo.ioc;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IocDemoTest {
    @Test
    void qualifierSelectsFormalGreeterDespiteCasualPrimaryAndDemoRuns() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("demo");
            context.register(CasualGreeter.class, Formalgreeter.class, GreetingService.class,
                    ReminderService.class, FormalGreetingService.class, PrototypeNote.class,
                    IocDemoRunner.class);
            context.refresh();

            FormalGreetingService formal = context.getBean(FormalGreetingService.class);
            assertSame(context.getBean(Formalgreeter.class), formal.exposeGreeter());
            assertNotSame(context.getBean(GreetingService.class).exposeGreeter(), formal.exposeGreeter());
            assertEquals("Kính chào Phuc.", formal.buildGreeting("Phuc"));
            context.getBean(IocDemoRunner.class).run();
        }
    }

    @Test
    void prototypeProviderCreatesInitializedInstancesWithoutAutomaticDestruction() {
        PrototypeNote first;
        PrototypeNote second;
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PrototypeNote.class)) {
            var provider = context.getBeanProvider(PrototypeNote.class);
            first = provider.getObject();
            second = provider.getObject();

            assertNotSame(first, second);
            assertTrue(first.isInitializedBySpring());
            assertTrue(second.isInitializedBySpring());
            assertFalse(first.isDestroyedBySpring());
            assertFalse(second.isDestroyedBySpring());
        }
        assertFalse(first.isDestroyedBySpring());
        assertFalse(second.isDestroyedBySpring());
    }

    @Test
    void springCreatesAndInjectsOneManagedGreeter() {
        CasualGreeter managed;
        CasualGreeter manual = new CasualGreeter();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CasualGreeter.class, Formalgreeter.class,
                    GreetingService.class, ReminderService.class);
            context.refresh();

            managed = context.getBean(CasualGreeter.class);
            assertSame(managed, context.getBean(GreetingService.class).exposeGreeter());
            assertSame(managed, context.getBean(ReminderService.class).exposeGreeter());
            assertTrue(managed.isInitializedBySpring());
            assertFalse(managed.isDestroyedBySpring());
            assertFalse(manual.isInitializedBySpring());
        }
        assertTrue(managed.isDestroyedBySpring());
        assertFalse(manual.isDestroyedBySpring());
    }
}
