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

import java.io.Serializable;

import jstatebox.io.OperationCodec;
import jstatebox.io.OperationCodecMapper;

/**
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
public class JavaOperationCodecMapper implements OperationCodecMapper {

  private static final OperationCodec INSTANCE = new JavaOperationCodec();

  @Override public String getCodecName() {
    return JavaOperationCodec.class.getName();
  }

  @Override public boolean isCodecFor(Object operation) {
    if (operation instanceof Serializable) {
      return true;
    } else {
      return false;
    }
  }

  @Override public OperationCodec getCodec() {
    return INSTANCE;
  }

}
