/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jstatebox.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * A statebox implementation for the JVM. Understands Java, Groovy, and Scala natively.
 * It allows asynchronous JVM applications to alter the state of objects without blocking
 * and by resolving conflicts at read time, rather than write time.
 *
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
public class Statebox<T> implements Serializable {

  protected static boolean IS_GROOVY_PRESENT;
  protected static boolean IS_SCALA_PRESENT = false;

  static {
    try {
      IS_GROOVY_PRESENT = Class.forName("groovy.lang.Closure") != null;
    } catch (ClassNotFoundException e) {
      IS_GROOVY_PRESENT = false;
    }
    try {
      IS_SCALA_PRESENT = Class.forName("scala.Any") != null;
    } catch (ClassNotFoundException e) {
      IS_SCALA_PRESENT = false;
    }
  }

  protected final T value;
  protected final SortedSet<StateboxOp> ops = new TreeSet<>();
  protected Long lastModified = System.currentTimeMillis();

  protected Statebox(T value) {
    this.value = value;
  }

  /**
   * Create a new statebox, wrapping the given value.
   *
   * @param value The immutable value of this statebox.
   */
  public static <T> Statebox<T> create(T value) {
    return new Statebox<>(value);
  }

  /**
   * Serialize a statebox to a ByteBuffer for saving to a file, sending to a DB, or sending via message.
   *
   * @param statebox The statebox to serialize.
   * @return The ByteBuffer containing the serialized statebox.
   * @throws IOException
   */
  public static <T> ByteBuffer serialize(Statebox<T> statebox) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ObjectOutputStream oout = new ObjectOutputStream(bout);
    oout.writeObject(statebox);
    oout.flush();

    return ByteBuffer.wrap(bout.toByteArray());
  }

  /**
   * Deserialize a statebox.
   *
   * @param buffer The buffer containing the serialized statebox.
   * @return The deserialized statebox.
   * @throws IOException
   * @throws ClassNotFoundException Usually thrown when the JVM into which this statebox is deserialized
   *                                doesn't have the classes assigned as operations.
   */
  @SuppressWarnings({"unchecked"})
  public static <T> Statebox<T> deserialize(ByteBuffer buffer) throws IOException, ClassNotFoundException {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(bytes));
    Statebox<T> statebox = (Statebox<T>) oin.readObject();

    return statebox;
  }

  /**
   * Retrieve the value originally set in this statebox.
   *
   * @return The immutable value of this statebox.
   */
  public T value() {
    return value;
  }

  /**
   * When this statebox was last modified or merged.
   *
   * @return The time (in milliseconds) when this statebox was last mutated or merged.
   */
  public Long lastModified() {
    return lastModified;
  }

  /**
   * Remove any operations that are older than the given age.
   *
   * @param age The number of milliseconds past which to expire operations.
   * @return This statebox with old operations removed.
   */
  public Statebox<T> expire(Long age) {
    List<StateboxOp> opsToRemove = new ArrayList<>();
    Long expiration = lastModified - age;
    for (StateboxOp op : ops) {
      if (op.timestamp < expiration) {
        opsToRemove.add(op);
      }
    }
    ops.removeAll(opsToRemove);

    return this;
  }

  /**
   * Mutate the value of this statebox into a new statebox that is the result of call
   * the operation and passing the current value.
   *
   * @param operation An operation to perform to mutate the value of this statebox into a new value.
   * @return A new statebox containing the result of this operation on the current statebox's value.
   */
  public <Op> Statebox<T> modify(Op operation) {
    ops.add(new StateboxOp(operation));
    lastModified = System.currentTimeMillis();

    return new Statebox<>(invoke(operation, value()));
  }

  /**
   * Merge the operations of the given stateboxes into a single value based on timestamp.
   *
   * @param stateboxes
   * @return
   */
  @SuppressWarnings({"unchecked"})
  public Statebox<T> merge(Statebox... stateboxes) {

    SortedSet<StateboxOp> mergedOps = new TreeSet<>();
    mergedOps.addAll(ops);

    for (Statebox st : stateboxes) {
      for (Object op : st.ops) {
        mergedOps.add((StateboxOp) op);
      }
    }

    T val = value();
    for (StateboxOp op : mergedOps) {
      val = invoke(op.operation, val);
    }

    lastModified = System.currentTimeMillis();

    return new Statebox<>(val);
  }

  @SuppressWarnings({"unchecked"})
  private T invoke(final Object op, final T value) {
    if (op instanceof Operation) {
      return ((Operation<T>) op).invoke(value);
    }

    if (IS_GROOVY_PRESENT) {
      if (op instanceof groovy.lang.Closure) {
        return (T) ((groovy.lang.Closure) op).call(value);
      }
    }

    if (IS_SCALA_PRESENT) {
      if (op instanceof scala.Function1) {
        return (T) ((scala.Function1) op).apply(value);
      }
    }

    if (op instanceof Runnable) {
      ((Runnable) op).run();
    }

    if (op instanceof Callable) {
      try {
        return ((Callable<T>) op).call();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    return null;
  }

  @Override public String toString() {
    return "\nStatebox {" +
        "\n\t value=" + value +
        ",\n\t ops=" + ops +
        ",\n\t lastModified=" + lastModified +
        "\n}";
  }

  private class StateboxOp implements Comparable<StateboxOp>, Serializable {

    private final Long timestamp = System.currentTimeMillis();
    private final Object operation;

    private StateboxOp(Object operation) {
      this.operation = operation;
    }

    @Override public int compareTo(StateboxOp op) {
      return timestamp.compareTo(op.timestamp);
    }

    @Override public String toString() {
      return "\nStateboxOp {" +
          "\n\t timestamp=" + timestamp +
          ",\n\t operation=" + operation +
          "\n}";
    }

  }

}
