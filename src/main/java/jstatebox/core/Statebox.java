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
import java.util.concurrent.CopyOnWriteArrayList;

import jstatebox.io.OperationCodec;
import jstatebox.io.OperationCodecMapper;
import jstatebox.io.groovy.GroovyOperationCodecMapper;
import jstatebox.io.java.JavaOperationCodecMapper;

/**
 * A statebox implementation for the JVM. Understands Java, Groovy, and Scala natively.
 * It allows asynchronous JVM applications to alter the state of objects without blocking
 * and by resolving conflicts at read time, rather than write time.
 *
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
public class Statebox<T> implements Serializable {

  protected static boolean IS_GROOVY_PRESENT = false;
  protected static boolean IS_SCALA_PRESENT = false;
  protected static boolean IS_CLOJURE_PRESENT = false;

  static {
    try {
      IS_GROOVY_PRESENT = Class.forName("groovy.lang.Closure") != null;
    } catch (ClassNotFoundException e) {}
    try {
      IS_SCALA_PRESENT = Class.forName("scala.Function1") != null;
    } catch (ClassNotFoundException e) {}
    try {
      IS_CLOJURE_PRESENT = Class.forName("clojure.lang.IFn") != null;
    } catch (ClassNotFoundException e) {}
  }

  public static final List<OperationCodecMapper> CODEC_MAPPERS = new CopyOnWriteArrayList<OperationCodecMapper>() {{
    if (IS_GROOVY_PRESENT) {
      add(new GroovyOperationCodecMapper());
    }
    add(new JavaOperationCodecMapper());
  }};

  protected final T origValue;
  protected T mutatedValue;
  protected final SortedSet<StateboxOp> ops = new TreeSet<>();
  protected Long lastModified = System.currentTimeMillis();

  protected Statebox(T value) {
    this.origValue = this.mutatedValue = value;
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
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ObjectOutputStream oout = new ObjectOutputStream(bout);
    oout.writeObject(statebox.origValue);
    oout.writeInt(statebox.ops.size());
    for (StateboxOp op : statebox.ops) {
      oout.writeLong(op.timestamp);
      OperationCodec codec = findCodecFor(op.operation);
      if (null == codec) {
        throw new IllegalStateException("No OperationCodec registered for " + op.operation);
      }
      oout.writeObject(codec.getClass().getName());
      oout.writeObject(codec.encode(op.operation, classLoader).array());
    }
    oout.close();

    return ByteBuffer.wrap(bout.toByteArray());
  }

  /**
   * Deserialize a statebox from a file, a DB, or from a message.
   *
   * @param buffer The buffer containing the serialized statebox.
   * @return The deserialized statebox.
   * @throws IOException
   * @throws ClassNotFoundException Usually thrown when the JVM into which this statebox is deserialized
   *                                doesn't have the classes assigned as operations.
   */
  @SuppressWarnings({"unchecked"})
  public static <T> Statebox<T> deserialize(ByteBuffer buffer) throws IOException, ClassNotFoundException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(bytes));

    T origValue = (T) oin.readObject();
    Statebox<T> statebox = new Statebox<>(origValue);
    int opsSize = oin.readInt();
    for (int i = 0; i < opsSize; i++) {
      Long timestamp = oin.readLong();
      String opCodecName = (String) oin.readObject();
      OperationCodec opCodec = findCodecForName(opCodecName);
      byte[] opBuff = (byte[]) oin.readObject();
      Object operation = opCodec.decode(ByteBuffer.wrap(opBuff), classLoader);
      StateboxOp stOp = new StateboxOp(operation);
      stOp.timestamp = timestamp;
      statebox.ops.add(stOp);
    }

    return statebox;
  }

  protected static OperationCodec findCodecForName(String name) {
    for (OperationCodecMapper mapper : CODEC_MAPPERS) {
      if (name.equals(mapper.getCodecName())) {
        return mapper.getCodec();
      }
    }
    return null;
  }

  protected static OperationCodec findCodecFor(Object operation) {
    for (OperationCodecMapper mapper : CODEC_MAPPERS) {
      if (mapper.isCodecFor(operation)) {
        return mapper.getCodec();
      }
    }
    return null;
  }

  /**
   * Retrieve the value originally set in this statebox.
   *
   * @return The immutable value of this statebox.
   */
  public T value() {
    return mutatedValue;
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
   * Truncate the operations to the last N.
   *
   * @param num
   * @return
   */
  @SuppressWarnings({"unchecked"})
  public Statebox<T> truncate(int num) {
    if (ops.size() > num) {
      Statebox<T> st = new Statebox<>(origValue);
      int size = ops.size();
      Object[] opsa = ops.toArray();
      // Get a subset of the last N operations
      for (int i = (size - num); i < size; i++) {
        st.ops.add((StateboxOp) opsa[i]);
      }

      return st;
    }

    return this;
  }

  /**
   * Mutate the value of this statebox into a new statebox that is the result of calling
   * the operation and passing the current value.
   *
   * @param operation An operation to perform to mutate the value of this statebox into a new value.
   * @return A new statebox containing the result of this operation on the current statebox's value.
   */
  public <Op> Statebox<T> modify(Op operation) {
    Statebox<T> statebox = new Statebox<>(origValue);
    statebox.mutatedValue = invoke(operation, origValue);
    statebox.ops.add(new StateboxOp(operation));
    lastModified = statebox.lastModified = System.currentTimeMillis();

    return statebox;
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

    if (IS_CLOJURE_PRESENT) {
      if (op instanceof clojure.lang.IFn) {
        return (T) ((clojure.lang.IFn) op).invoke(value);
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
        "\n\t value=" + mutatedValue +
        ",\n\t ops=" + ops +
        ",\n\t lastModified=" + lastModified +
        "\n}";
  }

  private static class StateboxOp implements Comparable<StateboxOp>, Serializable {

    private Long timestamp = System.currentTimeMillis();
    private Object operation;

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
