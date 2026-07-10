package org.enerscope.user.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordGeneratorTest {

    private final PasswordGenerator generator = new PasswordGenerator();

    @Test
    void generatesRequestedLength() {
        assertEquals(PasswordGenerator.DEFAULT_LENGTH, generator.generate().length());
        assertEquals(20, generator.generate(20).length());
    }

    @Test
    void meetsComplexityRequirements() {
        for (int i = 0; i < 200; i++) {
            String pw = generator.generate();
            assertTrue(pw.chars().anyMatch(Character::isLowerCase), "missing lower-case: " + pw);
            assertTrue(pw.chars().anyMatch(Character::isUpperCase), "missing upper-case: " + pw);
            assertTrue(pw.chars().anyMatch(Character::isDigit), "missing digit: " + pw);
            assertTrue(pw.chars().anyMatch(c -> !Character.isLetterOrDigit(c)), "missing symbol: " + pw);
        }
    }

    @Test
    void generatesDistinctPasswords() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(generator.generate());
        }
        // Collisions are astronomically unlikely for 16-char passwords.
        assertEquals(1000, seen.size());
    }

    @Test
    void rejectsTooShortLength() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(4));
    }
}
