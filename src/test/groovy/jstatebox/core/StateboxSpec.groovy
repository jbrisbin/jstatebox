package jstatebox.core

import spock.lang.Specification

/**
 * @author Jon Brisbin <jon@jbrisbin.com>
 */
class StateboxSpec extends Specification {

  def "Test Statebox"() {

    given:
    def st1 = Statebox.create("Hello")

    when:
    def st2 = st1.modify {value ->
      value + " "
    }
    def st3 = st1.modify {value ->
      value + "World!"
    }
    def st4 = st1.merge(st2, st3)

    then:
    st4.value() == "Hello World!"

  }

  def "Test that Statebox respects truncation"() {

    given:
    def st1 = Statebox.create("Hello")

    when:
    def st2 = st1.modify {value ->
      value + " "
    }
    def st3 = st1.modify {value ->
      value + "World!"
    }
    def st4 = st1.merge(st2.truncate(0), st3)

    then:
    st4.value() == "HelloWorld!"

  }

  def "Test serialization/deserialization"() {

    given:
    def st1 = Statebox.create("Hello")
    def st2 = st1.modify {value ->
      value + " "
    }
    def outBuff = Statebox.serialize(st2)
    def st3 = Statebox.deserialize(outBuff)
    def st4 = st1.modify({value ->
      value + "World!"
    })

    when:
    def st5 = st1.merge(st3, st4)

    then:
    st5.value() == "Hello World!"

  }

}
