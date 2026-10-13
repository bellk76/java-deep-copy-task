package com.example.deepcopy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Strategy for creating the target instance (path B, public J2SE only).
 *
 * <p>Attempt order:
 * <ol>
 *   <li>an accessible no-arg constructor;</li>
 *   <li>otherwise — an accessible constructor with the fewest parameters, invoked
 *       with default arguments ({@code null}/{@code 0}/{@code false}). All field
 *       values are then overwritten by the caller, so the default constructor
 *       arguments do not affect the result;</li>
 *   <li>if no constructor works — {@link DeepCopyException}.</li>
 * </ol>
 *
 * <p>Limitation: the constructor may have side effects. This does not apply to plain
 * data classes (including {@code Man} from the spec). The internal {@code sun.misc.Unsafe}
 * is intentionally not used — only public reflection.
 */
final class ObjectCreator {

    private ObjectCreator() {
    }

    /**
     * Creates a new instance of the given type.
     *
     * @param type a concrete (non-abstract, non-interface) class
     * @return an instance whose fields are not yet populated
     * @throws DeepCopyException if no suitable constructor is found or every attempt fails
     */
    static Object newInstance(Class<?> type) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new DeepCopyException("Cannot create an instance of an abstract type: " + type.getName());
        }

        Constructor<?> noArg = noArgConstructor(type);
        if (noArg != null) {
            try {
                return noArg.newInstance();
            } catch (ReflectiveOperationException e) {
                // the no-arg constructor failed — try the remaining ones below
            }
        }

        for (Constructor<?> ctor : constructorsByArity(type)) {
            if (ctor.getParameterCount() == 0) {
                continue; // already attempted above
            }
            if (!trySetAccessible(ctor)) {
                continue;
            }
            try {
                return ctor.newInstance(defaultArgs(ctor.getParameterTypes()));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // this constructor did not work — try the next one
            }
        }

        throw new DeepCopyException(
                "Cannot create an instance of " + type.getName() + ": no suitable constructor found");
    }

    private static Constructor<?> noArgConstructor(Class<?> type) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            return trySetAccessible(ctor) ? ctor : null;
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    private static Constructor<?>[] constructorsByArity(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));
        return constructors;
    }

    private static boolean trySetAccessible(Constructor<?> ctor) {
        try {
            return ctor.trySetAccessible();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Object[] defaultArgs(Class<?>[] parameterTypes) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i]);
        }
        return args;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return Character.valueOf('\0');
        }
        if (type == byte.class) {
            return Byte.valueOf((byte) 0);
        }
        if (type == short.class) {
            return Short.valueOf((short) 0);
        }
        if (type == int.class) {
            return Integer.valueOf(0);
        }
        if (type == long.class) {
            return Long.valueOf(0L);
        }
        if (type == float.class) {
            return Float.valueOf(0f);
        }
        if (type == double.class) {
            return Double.valueOf(0d);
        }
        return null;
    }
}
