package jstatebox.core;

import java.io.Serializable;

/**
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
public interface Operation<T> extends Serializable {

  public T invoke(T obj);

}
