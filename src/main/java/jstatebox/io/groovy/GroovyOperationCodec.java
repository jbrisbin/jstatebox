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

package jstatebox.io.groovy;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;
import jstatebox.io.ClassLoaderAwareObjectInputStream;
import jstatebox.io.OperationCodec;

/**
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
public class GroovyOperationCodec implements OperationCodec {

  @Override public ByteBuffer encode(Object operation, ClassLoader classLoader) throws IOException {
    List<ByteBuffer> buffers = new ArrayList<>();
    int totalSize = 0;
    Class<?> clazz = operation.getClass();
    String packagePath = packagePath(clazz);
    String innerClassPrefix = innerClassPrefix(clazz);

    // Encode supporting classes
    for (URLClassLoader cl : classLoaderHierarchy(classLoader)) {
      for (URL url : cl.getURLs()) {
        if (!"file".equals(url.getProtocol())) {
          continue;
        }

        File classes;
        try {
          classes = new File(url.toURI());
          if (!classes.exists()) {
            continue;
          }
        } catch (URISyntaxException e) {
          throw new IllegalStateException(e);
        }

        if (classes.isDirectory()) {
          File packageDir = null != packagePath ? new File(classes, packagePath) : classes;
          if (packageDir.exists()) {
            for (String classFile : packageDir.list(new PrefixBasedFilenameFilter(innerClassPrefix))) {
              ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(Paths.get(classFile)));
              buffers.add(buffer);
              totalSize += 4 + buffer.remaining();
            }
          }
        } else if (classes.getName().endsWith(".jar") || classes.getName().endsWith(".zip")) {
          ZipFile zipFile = new ZipFile(classes);
          if (null != (null != packagePath ? zipFile.getEntry(packagePath) : classes)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
              ZipEntry entry = entries.nextElement();
              String name = entry.getName();
              if (name.startsWith(innerClassPrefix) && name.endsWith(".class")) {
                ByteBuffer buffer = read(zipFile.getInputStream(entry));
                buffers.add(buffer);
                totalSize += 4 + buffer.remaining();
              }
            }
          }
        }
      }
    }

    // Encode the operation itself
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ObjectOutputStream oout = new ObjectOutputStream(bout);
    // Clear certain things about this
    if (operation instanceof Closure) {
      Closure cl = (Closure) operation;
      cl.getMetaClass().setAttribute(operation, "owner", null);
      cl.getMetaClass().setAttribute(operation, "thisObject", null);
      cl.setDelegate(null);
    }
    oout.writeObject(operation);
    oout.close();
    byte[] opBytes = bout.toByteArray();
    totalSize += 4 + opBytes.length;

    // Turn it all into a ByteBuffer
    ByteBuffer compositeBuffer = ByteBuffer.allocate(totalSize + 4);
    compositeBuffer.putInt(buffers.size());
    for (ByteBuffer bb : buffers) {
      compositeBuffer.putInt(bb.remaining());
      compositeBuffer.put(bb);
    }
    compositeBuffer.putInt(opBytes.length);
    compositeBuffer.put(opBytes);
    compositeBuffer.flip();

    return compositeBuffer;
  }

  @Override
  public Object decode(ByteBuffer buffer, ClassLoader classLoader) throws IOException, ClassNotFoundException {
    GroovyClassLoader gcl = new GroovyClassLoader(classLoader);
    int size = buffer.getInt();
    for (int i = 0; i < size; i++) {
      int len = buffer.getInt();
      byte[] bytes = new byte[len];
      buffer.get(bytes);
      gcl.defineClass(null, bytes);
    }

    int oSize = buffer.getInt();
    byte[] oBytes = new byte[oSize];
    buffer.get(oBytes);
    ClassLoaderAwareObjectInputStream oin = new ClassLoaderAwareObjectInputStream(new ByteArrayInputStream(oBytes),
                                                                                  gcl);
    return oin.readObject();
  }

  private List<URLClassLoader> classLoaderHierarchy(ClassLoader current) {
    List<URLClassLoader> classLoaders = new ArrayList<>();
    while (null != current) {
      if (current instanceof URLClassLoader) {
        classLoaders.add((URLClassLoader) current);
      }
      current = current.getParent();
    }
    return classLoaders;
  }

  private String innerClassPrefix(Class<?> clazz) {
    return clazz.getSimpleName().replace("$_$", "$_") + "_";
  }

  private String packagePath(Class<?> clazz) {
    if (null != clazz.getPackage()) {
      String name = clazz.getPackage().getName();
      if (null != name) {
        return name.replace('.', '/');
      }
    }
    return null;
  }

  private ByteBuffer read(InputStream in) throws IOException {
    BufferedInputStream bin = new BufferedInputStream(in);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    byte[] buff = new byte[16 * 1024];
    int read = bin.read(buff);
    while (read > 0) {
      bout.write(buff, 0, read);
    }
    return ByteBuffer.wrap(bout.toByteArray());
  }

  private class PrefixBasedFilenameFilter implements FilenameFilter {

    private String prefix;

    private PrefixBasedFilenameFilter(String prefix) {
      this.prefix = prefix;
    }

    @Override public boolean accept(File dir, String name) {
      return name.startsWith(prefix) && name.endsWith(".class");
    }

  }

}
