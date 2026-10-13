package com.example.deepcopy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates {@link CopyUtils#deepCopy(Object)}.
 *
 * <p>Shows that the copy is fully independent of the original while preserving the
 * topology of the object graph: cycles and shared references (aliasing).
 */
public final class Main {

    /** A recursive structure: a node references the next node (may form a cycle). */
    static final class Node {
        int value;
        Node next;

        Node(int value) {
            this.value = value;
        }
    }

    /** An object with fields of different categories: nested object, list, array, Map. */
    static final class Team {
        String name;
        Man captain;
        List<Man> members;
        int[] scores;
        Map<String, Man> byName;
    }

    private Main() {
    }

    public static void main(String[] args) {
        demonstrateManCopy();
        demonstrateSharedReference();
        demonstrateCycle();
        demonstrateNestedCollections();

        System.out.println();
        System.out.println("All checks passed.");
    }

    /** 1. The Man class from the spec: string, number and nested list are independent. */
    private static void demonstrateManCopy() {
        System.out.println("=== 1. Copy of the Man class (from the spec) ===");
        Man original = new Man("Ivan", 30,
                new ArrayList<>(List.of("War and Peace", "Crime and Punishment")));

        Man copy = CopyUtils.deepCopy(original);

        verify("original and copy are different objects", original != copy);
        verify("book lists are different objects",
                original.getFavoriteBooks() != copy.getFavoriteBooks());
        verify("book contents match before mutation",
                copy.getFavoriteBooks().equals(original.getFavoriteBooks()));

        original.setName("Peter");
        original.setAge(31);
        original.getFavoriteBooks().add("The Master and Margarita");

        verify("after mutating the original the copy's name is unchanged",
                "Ivan".equals(copy.getName()));
        verify("after mutating the original the copy's age is unchanged",
                copy.getAge() == 30);
        verify("after mutating the original the copy's books are unchanged",
                copy.getFavoriteBooks().equals(List.of("War and Peace", "Crime and Punishment")));

        System.out.println("Original: " + original.getName() + ", " + original.getAge()
                + ", " + original.getFavoriteBooks());
        System.out.println("Copy    : " + copy.getName() + ", " + copy.getAge()
                + ", " + copy.getFavoriteBooks());
    }

    /** 2. A shared reference: one object in two places stays one object in the copy too. */
    private static void demonstrateSharedReference() {
        System.out.println();
        System.out.println("=== 2. Shared reference (aliasing) ===");
        Man shared = new Man("Anna", 25, new ArrayList<>());
        Team team = new Team();
        team.name = "Team";
        team.captain = shared;
        team.members = new ArrayList<>(List.of(shared, new Man("Boris", 28, new ArrayList<>())));

        Team copy = CopyUtils.deepCopy(team);

        verify("in the original, captain is the same object as members[0]",
                team.captain == team.members.get(0));
        verify("in the copy, captain is the same object as members[0]",
                copy.captain == copy.members.get(0));
        verify("the copy's captain is not the original's captain object",
                copy.captain != team.captain);

        System.out.println("The shared-reference topology is preserved, but the objects are independent.");
    }

    /** 3. A recursive structure: a cycle in the object graph. */
    private static void demonstrateCycle() {
        System.out.println();
        System.out.println("=== 3. Cycle (recursive structure) ===");
        Node a = new Node(1);
        Node b = new Node(2);
        a.next = b;
        b.next = a; // cycle: a -> b -> a

        Node aCopy = CopyUtils.deepCopy(a);

        verify("the copy's root is a new object", aCopy != a);
        verify("the copy's next node is a new object", aCopy.next != a.next);
        verify("the cycle is preserved: aCopy.next.next == aCopy", aCopy.next.next == aCopy);
        verify("values are copied", aCopy.value == 1 && aCopy.next.value == 2);

        System.out.println("The cycle is broken without infinite recursion and reproduced in the copy.");
    }

    /** 4. Nested collections, arrays and Map: independence and preserved aliasing. */
    private static void demonstrateNestedCollections() {
        System.out.println();
        System.out.println("=== 4. Nested collections / arrays / Map ===");
        Man leader = new Man("Leader", 40, new ArrayList<>(List.of("Book")));
        Team team = new Team();
        team.name = "Department";
        team.captain = leader;
        team.members = new ArrayList<>(List.of(leader, new Man("Member", 22, new ArrayList<>())));
        team.scores = new int[]{10, 20, 30};
        team.byName = new LinkedHashMap<>();
        team.byName.put("Leader", leader);

        Team copy = CopyUtils.deepCopy(team);

        copy.scores[0] = 999;
        copy.members.get(1).setName("Changed");
        copy.byName.get("Leader").setAge(41);

        verify("the array in the copy is independent of the original", team.scores[0] == 10);
        verify("a nested list element is independent", "Member".equals(team.members.get(1).getName()));
        verify("the object from the Map is independent", team.captain.getAge() == 40);
        verify("aliasing is preserved: byName == captain inside the copy",
                copy.byName.get("Leader") == copy.captain);
        verify("the original's captain is not linked to the copy", copy.captain != team.captain);

        System.out.println("Arrays, collections and Map are copied deeply; aliasing is preserved.");
    }

    private static void verify(String description, boolean condition) {
        System.out.println((condition ? "[OK]   " : "[FAIL] ") + description);
        if (!condition) {
            throw new IllegalStateException("Check failed: " + description);
        }
    }
}
