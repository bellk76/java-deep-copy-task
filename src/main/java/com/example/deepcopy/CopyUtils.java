package com.example.deepcopy;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Universal deep copy of objects of arbitrary structure.
 *
 * <p>The object graph is traversed using reflection. To break cycles and preserve
 * the topology of shared references, an {@link IdentityHashMap} (original → copy)
 * is used: the copy is registered there <b>before</b> its fields/elements are traversed.
 *
 * <p>Only the standard J2SE public API is used. {@code java.io.Serializable},
 * {@code java.lang.Cloneable} and {@code sun.misc.Unsafe} are intentionally avoided.
 *
 * <p>Known limitations:
 * <ul>
 *   <li>{@code transient} fields are copied like regular fields;</li>
 *   <li>non-static inner classes cannot be copied (their constructor requires the
 *       enclosing instance) and cause a {@link DeepCopyException};</li>
 *   <li>cyclic structures that pass through a {@code record} are not supported and
 *       cause a {@link DeepCopyException};</li>
 *   <li>unmodifiable sorted views are reproduced as {@code SortedSet}/{@code SortedMap}
 *       wrappers, so a {@code NavigableSet}/{@code NavigableMap} type is not preserved.</li>
 * </ul>
 */
public final class CopyUtils {

    /** Sentinel marking an object whose copy is currently being built (cycle detection). */
    private static final Object IN_PROGRESS = new Object();

    /** Immutable types: copied by reference. */
    private static final Set<Class<?>> IMMUTABLE_TYPES = Set.of(
            Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
            Character.class, Float.class, Double.class,
            String.class, BigInteger.class, BigDecimal.class, Class.class,
            java.util.UUID.class, java.util.Locale.class, java.util.Currency.class,
            java.util.regex.Pattern.class, java.io.File.class,
            OptionalInt.class, OptionalLong.class, OptionalDouble.class,
            java.time.LocalDate.class, java.time.LocalTime.class, java.time.LocalDateTime.class,
            java.time.Instant.class, java.time.Duration.class, java.time.Period.class,
            java.time.ZonedDateTime.class, java.time.OffsetDateTime.class, java.time.OffsetTime.class,
            java.time.Year.class, java.time.YearMonth.class, java.time.MonthDay.class,
            java.time.ZoneId.class, java.time.ZoneOffset.class);

    private CopyUtils() {
    }

    /**
     * Returns a full (deep) copy of the given object.
     *
     * @param source the source object (may be {@code null})
     * @param <T>    the object type
     * @return an independent copy; {@code null} for {@code null}
     * @throws DeepCopyException if the object cannot be copied
     */
    public static <T> T deepCopy(T source) {
        return copy(source, new IdentityHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private static <T> T copy(Object source, IdentityHashMap<Object, Object> memo) {
        if (source == null) {
            return null;
        }
        if (memo.containsKey(source)) {
            Object cached = memo.get(source);
            if (cached == IN_PROGRESS) {
                throw new DeepCopyException(
                        "Cyclic reference through " + source.getClass().getName() + " is not supported");
            }
            return (T) cached;
        }

        Class<?> type = source.getClass();
        if (type.isArray()) {
            return (T) copyArray(source, memo);
        }
        if (isImmutable(source)) {
            return (T) source;
        }
        if (source instanceof Optional<?> optional) {
            return (T) copyOptional(optional, memo);
        }
        if (source instanceof Map<?, ?>) {
            return (T) copyMap(source, memo);
        }
        if (source instanceof Collection<?>) {
            return (T) copyCollection(source, memo);
        }
        if (type.isRecord()) {
            return (T) copyRecord(source, memo);
        }
        return (T) copyObject(source, memo);
    }

    /** The object is inherently immutable (wrappers, String, enum, java.time, etc.). */
    private static boolean isImmutable(Object source) {
        Class<?> type = source.getClass();
        if (type.isEnum()) {
            return true;
        }
        if (IMMUTABLE_TYPES.contains(type)) {
            return true;
        }
        return type.getPackageName().equals("java.time");
    }

    private static boolean isImmutableCollection(Object source) {
        String className = source.getClass().getName();
        return className.startsWith("java.util.ImmutableCollections")
                || className.startsWith("java.util.Collections$Singleton")
                || className.startsWith("java.util.Collections$Empty");
    }

    private static boolean isUnmodifiableCollection(Object source) {
        return source.getClass().getName().startsWith("java.util.Collections$Unmodifiable");
    }

    // ------------------------------------------------------------------
    // Arrays
    // ------------------------------------------------------------------

    private static Object copyArray(Object source, IdentityHashMap<Object, Object> memo) {
        Class<?> componentType = source.getClass().getComponentType();
        int length = Array.getLength(source);
        Object copy = Array.newInstance(componentType, length);
        memo.put(source, copy);
        if (componentType.isPrimitive()) {
            System.arraycopy(source, 0, copy, 0, length);
        } else {
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, copy(Array.get(source, i), memo));
            }
        }
        return copy;
    }

    // ------------------------------------------------------------------
    // Optional
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Optional<Object> copyOptional(Optional<?> source, IdentityHashMap<Object, Object> memo) {
        if (source.isEmpty()) {
            return Optional.empty();
        }
        Object value = copy(source.get(), memo);
        return Optional.of(value);
    }

    // ------------------------------------------------------------------
    // Collections
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Object copyCollection(Object source, IdentityHashMap<Object, Object> memo) {
        Collection<Object> src = (Collection<Object>) source;
        boolean immutable = isImmutableCollection(source);
        boolean unmodifiable = isUnmodifiableCollection(source);

        Collection<Object> base = createCollection(src);
        memo.put(source, base);
        for (Object element : src) {
            base.add(copy(element, memo));
        }

        Collection<Object> result;
        if (immutable) {
            result = src instanceof Set<?> ? Set.copyOf(base) : List.copyOf(base);
        } else if (unmodifiable) {
            result = wrapUnmodifiable(base);
        } else {
            result = base;
        }
        if (result != base) {
            memo.put(source, result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> createCollection(Collection<?> src) {
        if (src instanceof SortedSet<?> sortedSet) {
            Comparator<Object> comparator = (Comparator<Object>) sortedSet.comparator();
            return new TreeSet<>(comparator);
        }
        if (src instanceof Set<?>) {
            return new LinkedHashSet<>();
        }
        if (src instanceof Deque<?> || src instanceof Queue<?>) {
            return new ArrayDeque<>();
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> wrapUnmodifiable(Collection<Object> base) {
        if (base instanceof List<?>) {
            return Collections.unmodifiableList((List<Object>) base);
        }
        if (base instanceof SortedSet<?>) {
            return Collections.unmodifiableSortedSet((SortedSet<Object>) base);
        }
        if (base instanceof Set<?>) {
            return Collections.unmodifiableSet((Set<Object>) base);
        }
        return Collections.unmodifiableCollection(base);
    }

    // ------------------------------------------------------------------
    // Map
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Object copyMap(Object source, IdentityHashMap<Object, Object> memo) {
        Map<Object, Object> src = (Map<Object, Object>) source;
        boolean immutable = isImmutableCollection(source);
        boolean unmodifiable = isUnmodifiableCollection(source);

        Map<Object, Object> base;
        if (src instanceof SortedMap<?, ?> sortedMap) {
            Comparator<Object> comparator = (Comparator<Object>) sortedMap.comparator();
            base = new TreeMap<>(comparator);
        } else {
            base = new LinkedHashMap<>();
        }
        memo.put(source, base);
        for (Map.Entry<Object, Object> entry : src.entrySet()) {
            base.put(copy(entry.getKey(), memo), copy(entry.getValue(), memo));
        }

        Map<Object, Object> result;
        if (immutable) {
            result = Map.copyOf(base);
        } else if (unmodifiable) {
            result = wrapUnmodifiableMap(base);
        } else {
            result = base;
        }
        if (result != base) {
            memo.put(source, result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> wrapUnmodifiableMap(Map<Object, Object> base) {
        if (base instanceof SortedMap<?, ?>) {
            return Collections.unmodifiableSortedMap((SortedMap<Object, Object>) base);
        }
        return Collections.unmodifiableMap(base);
    }

    // ------------------------------------------------------------------
    // records
    // ------------------------------------------------------------------

    private static Object copyRecord(Object source, IdentityHashMap<Object, Object> memo) {
        Class<?> type = source.getClass();
        memo.put(source, IN_PROGRESS);

        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            parameterTypes[i] = component.getType();
            Method accessor = component.getAccessor();
            try {
                if (!accessor.trySetAccessible()) {
                    throw new DeepCopyException("No access to record component " + component.getName());
                }
                arguments[i] = copy(accessor.invoke(source), memo);
            } catch (ReflectiveOperationException e) {
                throw new DeepCopyException(
                        "Cannot read record component " + component.getName() + " of " + type.getName(), e);
            }
        }
        try {
            Constructor<?> canonical = type.getDeclaredConstructor(parameterTypes);
            if (!canonical.trySetAccessible()) {
                throw new DeepCopyException("No access to the canonical constructor of " + type.getName());
            }
            Object copy = canonical.newInstance(arguments);
            memo.put(source, copy);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new DeepCopyException("Cannot create a copy of record " + type.getName(), e);
        }
    }

    // ------------------------------------------------------------------
    // Plain objects
    // ------------------------------------------------------------------

    private static Object copyObject(Object source, IdentityHashMap<Object, Object> memo) {
        Class<?> type = source.getClass();
        Object copy = ObjectCreator.newInstance(type);
        memo.put(source, copy);
        copyFields(source, copy, type, memo);
        return copy;
    }

    private static void copyFields(Object source, Object target, Class<?> type, IdentityHashMap<Object, Object> memo) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                    continue;
                }
                try {
                    if (!field.trySetAccessible()) {
                        continue;
                    }
                    Object value = field.get(source);
                    field.set(target, copy(value, memo));
                } catch (IllegalAccessException e) {
                    throw new DeepCopyException(
                            "Cannot copy field " + field.getName() + " of " + type.getName(), e);
                }
            }
        }
    }
}
