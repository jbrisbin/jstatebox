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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import groovy.lang.Closure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Function1;

/**
 * A statebox implementation for the JVM. Understands Java, Groovy, and Scala natively.
 * It allows asynchronous JVM applications to alter the state of objects without blocking
 * and by resolving conflicts at read time, rather than write time.
 *
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
public class Statebox<T> implements Serializable {

  protected final Logger log = LoggerFactory.getLogger(getClass());

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
   * @param value
   */
  public static <T> Statebox<T> create(T value) {
    return new Statebox<>(value);
  }

  /**
   * Retrieve the value originally set in this statebox.
   *
   * @return
   */
  public T value() {
    return value;
  }

  /**
   * When this statebox was last modified or merged.
   *
   * @return
   */
  public Long lastModified() {
    return lastModified;
  }

  /**
   * Remove any operations that are older than the given age.
   *
   * @param age
   * @return
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
   * @param operation
   * @param <Op>
   * @return
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
    for (Statebox st : stateboxes) {
      for (Object op : st.ops) {
        ops.add((StateboxOp) op);
      }
    }

    T val = value();
    for (StateboxOp op : ops) {
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
      if (op instanceof Closure) {
        return (T) ((Closure) op).call(value);
      }
    }

    if (IS_SCALA_PRESENT) {
      if (op instanceof Function1) {
        return (T) ((Function1) op).apply(value);
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

  private class StateboxOp implements Comparable<StateboxOp> {

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
