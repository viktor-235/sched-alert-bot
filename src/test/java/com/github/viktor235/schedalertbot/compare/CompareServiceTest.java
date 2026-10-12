package com.github.viktor235.schedalertbot.compare;

import com.github.viktor235.schedalertbot.site.stopgame.model.SgEventWeb;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompareServiceTest {

    record Person(String name, int age) {
    }

    @Test
    void compare_whenDifferences_thenDiff() {
        // Arrange
        CompareService compareService = new CompareService();
        Person person1 = new Person("Alice", 30);
        Person person2 = new Person("Alice", 31);

        // Act
        List<FieldDiff> fieldDiffs = compareService.compare(person1, person2);

        // Assert
        System.out.println(fieldDiffs);
        assertEquals(1, fieldDiffs.size());
        FieldDiff fieldDiff = fieldDiffs.getFirst();
        assertEquals("age", fieldDiff.getName());
        assertEquals(30, fieldDiff.getOldValue());
        assertEquals(31, fieldDiff.getNewValue());
    }

    @Test
    void compare_whenLeftIsNull_thenDiff() {
        // Arrange
        CompareService compareService = new CompareService();
        Person person1 = new Person(null, 30);
        Person person2 = new Person("Alice", 30);

        // Act
        List<FieldDiff> fieldDiffs = compareService.compare(person1, person2);

        // Assert
        System.out.println(fieldDiffs);
        assertEquals(1, fieldDiffs.size());
        FieldDiff fieldDiff = fieldDiffs.getFirst();
        assertEquals("name", fieldDiff.getName());
        assertNull(fieldDiff.getOldValue());
        assertEquals("Alice", fieldDiff.getNewValue());
    }

    @Test
    void compare_whenRightIsNull_thenDiff() {
        // Arrange
        CompareService compareService = new CompareService();
        Person person1 = new Person("Alice", 30);
        Person person2 = new Person(null, 30);

        // Act
        List<FieldDiff> fieldDiffs = compareService.compare(person1, person2);

        // Assert
        System.out.println(fieldDiffs);
        assertEquals(1, fieldDiffs.size());
        FieldDiff fieldDiff = fieldDiffs.getFirst();
        assertEquals("name", fieldDiff.getName());
        assertEquals("Alice", fieldDiff.getOldValue());
        assertNull(fieldDiff.getNewValue());
    }

    @Test
    void compare_whenIdentical_thenEmptyDiff() {
        // Arrange
        CompareService compareService = new CompareService();
        Person person1 = new Person("Alice", 30);
        Person person2 = new Person("Alice", 30);

        // Act
        List<FieldDiff> fieldDiffs = compareService.compare(person1, person2);

        // Assert
        System.out.println(fieldDiffs);
        assertEquals(0, fieldDiffs.size());
    }

    @Test
    void compare_whenEventsDifferOnlyInNowLive_thenEmptyDiff() {
        // Arrange
        CompareService compareService = new CompareService();
        SgEventWeb scheduled = SgEventWeb.builder().id("1").name("Stream").nowLive(false).build();
        SgEventWeb live = scheduled.toBuilder().nowLive(true).build();

        // Act
        List<FieldDiff> fieldDiffs = compareService.compare(scheduled, live);

        // Assert
        assertEquals(0, fieldDiffs.size());
    }
}
