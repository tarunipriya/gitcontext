package com.example;

import java.util.List;
import java.util.Map;

/**
 * Service for sample operations.
 */
@Deprecated
public class SampleService {

    private final String name;

    /**
     * Constructs a new SampleService.
     */
    public SampleService(String name) {
        this.name = name;
    }

    /**
     * Greets the user.
     */
    @Override
    public String greet(String target) {
        return "Hello " + target;
    }

    public void process() {
        System.out.println("Processing");
    }

    public int getX() {
        return 1;
    }

    public int getY() {
        return 2;
    }

    public static class InnerHelper {
        public void assist() {
            System.out.println("Assisting");
        }
    }
}

interface Worker {
    void work();
}

enum Status {
    ACTIVE,
    INACTIVE
}

record UserRecord(String id, String email) {}
