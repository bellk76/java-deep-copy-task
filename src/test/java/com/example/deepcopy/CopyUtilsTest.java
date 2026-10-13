package com.example.deepcopy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CopyUtils.deepCopy")
class CopyUtilsTest {

    enum Day {MONDAY, FRIDAY}

    /** A class with only a parameterized constructor and final fields. */
    static final class Box {
        private final String label;
        private final List<String> items;

        Box(String label, List<String> items) {
            this.label = label;
            this.items = items;
        }

        String label() {
            return label;
        }

        List<String> items() {
            return items;
        }
    }

    /** A recursive structure (may form a cycle). */
    static final class Node {
        int value;
        Node next;

        Node(int value) {
            this.value = value;
        }
    }

    /** A class with a validating constructor: it cannot be recreated correctly. */
    static final class Validated {
        final int value;

        Validated(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("value must be positive");
            }
            this.value = value;
        }
    }

    record Bag(String name, List<String> items) {
    }

    record Holder(List<Object> items) {
    }

    @Test
    @DisplayName("null returns null")
    void nullReturnsNull() {
        assertNull(CopyUtils.deepCopy(null));
    }

    @Nested
    @DisplayName("Primitives and immutable types")
    class Immutable {

        @Test
        @DisplayName("wrappers and String are copied by reference")
        void wrappersAndStringByReference() {
            assertSame("abc", CopyUtils.deepCopy("abc"));
            assertEquals(Integer.valueOf(42), CopyUtils.deepCopy(42));
            assertSame(Day.MONDAY, CopyUtils.deepCopy(Day.MONDAY));
        }

        @Test
        @DisplayName("Optional deep-copies its value")
        void optional() {
            Man man = new Man("Ivan", 30, new ArrayList<>());
            Optional<Man> source = Optional.of(man);
            Optional<Man> copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertNotSame(source.get(), copy.get());
            assertEquals("Ivan", copy.get().getName());
            assertSame(Optional.empty(), CopyUtils.deepCopy(Optional.empty()));
        }
    }

    @Nested
    @DisplayName("Arrays")
    class ArrayTests {

        @Test
        @DisplayName("a primitive array is copied element-wise")
        void primitiveArray() {
            int[] source = {1, 2, 3};
            int[] copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertArrayEquals(source, copy);
            copy[0] = 99;
            assertEquals(1, source[0]);
        }

        @Test
        @DisplayName("a multi-dimensional array is copied recursively")
        void multiDimensionalArray() {
            int[][] source = {{1, 2}, {3, 4}};
            int[][] copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertNotSame(source[0], copy[0]);
            assertArrayEquals(source[1], copy[1]);
            copy[1][1] = 99;
            assertEquals(4, source[1][1]);
        }

        @Test
        @DisplayName("an object array is copied deeply")
        void objectArray() {
            Man[] source = {new Man("A", 1, new ArrayList<>()), new Man("B", 2, new ArrayList<>())};
            Man[] copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertNotSame(source[0], copy[0]);
            assertEquals("A", copy[0].getName());
        }
    }

    @Nested
    @DisplayName("Collections")
    class CollectionTests {

        @Test
        @DisplayName("a List is copied deeply")
        void list() {
            List<Man> source = new ArrayList<>(List.of(new Man("A", 1, new ArrayList<>())));
            List<Man> copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertNotSame(source.get(0), copy.get(0));
            assertEquals(source.get(0).getName(), copy.get(0).getName());
        }

        @Test
        @DisplayName("a Set keeps its type and copies elements")
        void set() {
            Set<Man> source = new TreeSet<>(java.util.Comparator.comparing(Man::getName));
            source.add(new Man("A", 1, new ArrayList<>()));
            Set<Man> copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertEquals(1, copy.size());
            assertNotSame(source.iterator().next(), copy.iterator().next());
        }

        @Test
        @DisplayName("a Deque is copied deeply")
        void deque() {
            Deque<Man> source = new ArrayDeque<>();
            source.add(new Man("A", 1, new ArrayList<>()));
            Deque<Man> copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertNotSame(source.peek(), copy.peek());
        }

        @Test
        @DisplayName("List.of produces an immutable copy")
        void immutableList() {
            List<Man> source = List.of(new Man("A", 1, new ArrayList<>()));
            List<Man> copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertNotSame(source.get(0), copy.get(0));
            assertThrows(UnsupportedOperationException.class,
                    () -> copy.add(new Man("B", 2, new ArrayList<>())));
        }

        @Test
        @DisplayName("an unmodifiable collection stays unmodifiable")
        void unmodifiableList() {
            List<String> source = Collections.unmodifiableList(new ArrayList<>(List.of("a", "b")));
            List<String> copy = CopyUtils.deepCopy(source);
            assertEquals(List.of("a", "b"), copy);
            assertThrows(UnsupportedOperationException.class, () -> copy.add("c"));
        }
    }

    @Nested
    @DisplayName("Map")
    class MapTests {

        @Test
        @DisplayName("keys and values are copied deeply")
        void hashMap() {
            Map<String, Man> source = new HashMap<>();
            source.put("k", new Man("A", 1, new ArrayList<>()));
            Map<String, Man> copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertNotSame(source.get("k"), copy.get("k"));
            assertEquals("A", copy.get("k").getName());
        }

        @Test
        @DisplayName("a TreeMap keeps its order and comparator")
        void treeMap() {
            Map<String, Integer> source = new TreeMap<>();
            source.put("b", 2);
            source.put("a", 1);
            Map<String, Integer> copy = CopyUtils.deepCopy(source);
            assertEquals(List.of("a", "b"), new ArrayList<>(copy.keySet()));
        }

        @Test
        @DisplayName("Map.of produces an immutable copy")
        void immutableMap() {
            Map<String, Man> source = Map.of("k", new Man("A", 1, new ArrayList<>()));
            Map<String, Man> copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertNotSame(source.get("k"), copy.get("k"));
            assertThrows(UnsupportedOperationException.class, () -> copy.put("x", null));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("a cycle is preserved without infinite recursion")
        void cycle() {
            Node a = new Node(1);
            Node b = new Node(2);
            a.next = b;
            b.next = a;

            Node copy = CopyUtils.deepCopy(a);

            assertNotSame(a, copy);
            assertNotSame(b, copy.next);
            assertSame(copy, copy.next.next);
            assertEquals(1, copy.value);
            assertEquals(2, copy.next.value);
        }

        @Test
        @DisplayName("a shared reference is preserved and objects are independent")
        void sharedReference() {
            Man shared = new Man("Anna", 25, new ArrayList<>());
            List<Man> source = new ArrayList<>(List.of(shared, shared));

            List<Man> copy = CopyUtils.deepCopy(source);

            assertSame(copy.get(0), copy.get(1));
            assertNotSame(shared, copy.get(0));
        }

        @Test
        @DisplayName("a record is copied via its canonical constructor")
        void recordCopy() {
            Bag source = new Bag("bag", new ArrayList<>(List.of("a", "b")));
            Bag copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertTrue(copy.getClass().isRecord());
            assertEquals(source.name(), copy.name());
            assertNotSame(source.items(), copy.items());
            assertEquals(source.items(), copy.items());
            copy.items().add("c");
            assertEquals(2, source.items().size());
        }

        @Test
        @DisplayName("final fields of a class without a no-arg constructor are copied")
        void finalFields() {
            Box source = new Box("box", new ArrayList<>(List.of("x")));
            Box copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertEquals("box", copy.label());
            assertNotSame(source.items(), copy.items());
            copy.items().add("y");
            assertTrue(source.items().size() == 1);
            assertFalse(copy.items().size() == 1);
        }

        @Test
        @DisplayName("an object that cannot be recreated throws DeepCopyException")
        void uncopyableThrows() {
            Validated source = new Validated(5);
            assertThrows(DeepCopyException.class, () -> CopyUtils.deepCopy(source));
        }

        @Test
        @DisplayName("an array of immutable elements is still deep-copied")
        void immutableElementArray() {
            java.time.LocalDate[] source = {
                    java.time.LocalDate.of(2020, 1, 1),
                    java.time.LocalDate.of(2021, 2, 2)};
            java.time.LocalDate[] copy = CopyUtils.deepCopy(source);
            assertNotSame(source, copy);
            assertArrayEquals(source, copy);
            copy[0] = java.time.LocalDate.of(1999, 9, 9);
            assertEquals(java.time.LocalDate.of(2020, 1, 1), source[0]);
        }

        @Test
        @DisplayName("a cycle through a record throws a clear exception")
        void recordCycleThrows() {
            List<Object> list = new ArrayList<>();
            Holder holder = new Holder(list);
            list.add(holder);
            assertThrows(DeepCopyException.class, () -> CopyUtils.deepCopy(holder));
        }

        @Test
        @DisplayName("a cycle through an unmodifiable wrapper does not overflow")
        void unmodifiableCycle() {
            List<Object> list = new ArrayList<>();
            List<Object> unmodifiable = Collections.unmodifiableList(list);
            list.add(unmodifiable);
            List<Object> copy = CopyUtils.deepCopy(unmodifiable);
            assertNotSame(unmodifiable, copy);
            assertThrows(UnsupportedOperationException.class, () -> copy.add("x"));
        }

        @Test
        @DisplayName("singleton and empty collections stay immutable")
        void singletonAndEmptyStayImmutable() {
            List<String> singleton = Collections.singletonList("x");
            List<String> singletonCopy = CopyUtils.deepCopy(singleton);
            assertEquals(List.of("x"), singletonCopy);
            assertThrows(UnsupportedOperationException.class, () -> singletonCopy.add("y"));

            List<String> empty = Collections.emptyList();
            List<String> emptyCopy = CopyUtils.deepCopy(empty);
            assertTrue(emptyCopy.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> emptyCopy.add("y"));
        }

        @Test
        @DisplayName("deep nesting is fully independent")
        void nestedStructure() {
            Man leader = new Man("Leader", 40, new ArrayList<>(List.of("Book")));
            Map<String, Man> byName = new HashMap<>();
            byName.put("Leader", leader);
            List<Object> source = new ArrayList<>(List.of(
                    leader,
                    new int[]{10, 20},
                    byName,
                    Optional.of(leader)));

            List<Object> copy = CopyUtils.deepCopy(source);

            assertNotSame(source, copy);
            assertNotSame(leader, copy.get(0));
            assertNotSame(source.get(1), copy.get(1));
            assertNotSame(byName, copy.get(2));
            @SuppressWarnings("unchecked")
            Map<String, Man> copiedByName = (Map<String, Man>) copy.get(2);
            assertSame(copy.get(0), copiedByName.get("Leader"));
        }
    }
}
