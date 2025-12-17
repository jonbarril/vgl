package com.vgl.cli.test.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper to print test progress as "[ClassName current/total: testName]...".
 * Calculates the total by counting @Test methods on the test class via reflection.
 */
public final class TestProgress {
    private static final Map<Class<?>, AtomicInteger> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Integer> TOTALS = new ConcurrentHashMap<>();

    private TestProgress() {}

    public static void print(Class<?> testClass, String testName) {
        AtomicInteger counter = COUNTERS.computeIfAbsent(testClass, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();
        int total = TOTALS.computeIfAbsent(testClass, k -> computeTotalTests(k));
        System.out.println("[" + testClass.getSimpleName() + " " + current + "/" + total + ": " + testName + "]...");
        System.out.flush();
    }

    private static int computeTotalTests(Class<?> testClass) {
        try {
            Method[] methods = testClass.getDeclaredMethods();
            return (int) Arrays.stream(methods).filter(m -> m.isAnnotationPresent(Test.class)).count();
        } catch (Exception e) {
            return 0;
        }
    }
}
