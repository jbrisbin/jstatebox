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
    def st2 = st1.modify({value ->
      value + " "
    })
    def st3 = st1.modify({value ->
      value + "World!"
    })
    def st4 = st1.merge(st2, st3)

    then:
    st4.value() == "Hello World!"

  }

}
