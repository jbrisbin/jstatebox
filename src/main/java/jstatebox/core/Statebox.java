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

  public static <T> Statebox<T> create(T value) {
    return new Statebox<>(value);
  }

  public T value() {
    return value;
  }

  public Long lastModified() {
    return lastModified;
  }

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

  public <Op> Statebox<T> modify(Op operation) {
    ops.add(new StateboxOp(operation));
    return new Statebox<>(invoke(operation, value()));
  }

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

  private class OpRunner<T> implements Runnable {

    private T obj;
    private final Object op;

    private OpRunner(Object op) {
      this.op = op;
    }

    @SuppressWarnings({"unchecked"})
    @Override public void run() {
      if (op instanceof Operation) {
        obj = ((Operation<T>) op).invoke(obj);
        return;
      }

      if (IS_GROOVY_PRESENT) {
        if (op instanceof Closure) {
          obj = (T) ((Closure) op).call(obj);
          return;
        }
      }

      if (IS_SCALA_PRESENT) {
        if (op instanceof Function1) {
          obj = (T) ((Function1) op).apply(obj);
          return;
        }
      }

      if (op instanceof Runnable) {
        ((Runnable) op).run();
        return;
      }

      if (op instanceof Callable) {
        try {
          obj = ((Callable<T>) op).call();
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
        return;
      }
    }

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
