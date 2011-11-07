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

package jstatebox.io.java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

import jstatebox.io.ClassLoaderAwareObjectInputStream;
import jstatebox.io.OperationCodec;

/**
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
public class JavaOperationCodec implements OperationCodec {

  @Override public ByteBuffer encode(Object operation, ClassLoader classLoader) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ObjectOutputStream oout = new ObjectOutputStream(bout);
    oout.writeObject(operation);
    oout.flush();
    oout.close();
    bout.flush();

    return ByteBuffer.wrap(bout.toByteArray());
  }

  @Override
  public Object decode(ByteBuffer buffer, ClassLoader classLoader) throws IOException, ClassNotFoundException {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    ClassLoaderAwareObjectInputStream oin = new ClassLoaderAwareObjectInputStream(new ByteArrayInputStream(bytes),
                                                                                  classLoader);
    return oin.readObject();
  }

}
