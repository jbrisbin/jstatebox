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

package jstatebox.core

/**
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
class SerializationTest {

  static def serialize(statebox) {
    def fout = new FileOutputStream("statebox_test")
    def oout = new ObjectOutputStream(fout)
    oout.writeObject(statebox)
    oout.flush()
    fout.flush()
  }

  static def deserialize() {
    def fin = new FileInputStream("statebox_test")
    def oin = new ObjectInputStream(fin)
    def statebox = oin.readObject()

    return statebox
  }

}
