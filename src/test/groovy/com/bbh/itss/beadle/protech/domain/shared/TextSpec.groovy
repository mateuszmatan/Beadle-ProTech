package com.bbh.itss.beadle.protech.domain.shared

import spock.lang.Specification

import static com.bbh.itss.beadle.protech.domain.shared.Text.abbreviateBytes
import static com.bbh.itss.beadle.protech.domain.shared.Text.bytes
import static com.bbh.itss.beadle.protech.domain.shared.Text.clean

class TextSpec extends Specification {

    def "clean keeps the first occurrence of each value that is not blank, trimmed, in their order"() {
        expect:
        clean(['b', 'a', ' b', '', null, ' ']) == ['b', 'a']
        clean(null) == []
    }

    def "abbreviate cuts a text to the UTF-8 bytes of an Oracle column on a character boundary"() {
        expect:
        bytes('zażółć') == 10
        abbreviateBytes(null, 5) == null
        abbreviateBytes('abcdef', 6) == 'abcdef'
        abbreviateBytes('abc  defgh', 8) == 'abc...'
        abbreviateBytes('ąąąąą', 9) == 'ąąą...'
        abbreviateBytes('ab😀cd', 7) == 'ab...'
        bytes(abbreviateBytes('é' * 300, 160)) <= 160
    }

    def "the cleaned list cannot be changed, and a URL is told by its scheme"() {
        when:
        clean(['a']).add('b')

        then:
        thrown(UnsupportedOperationException)
        Text.isUrl('https://bbh.com') && Text.isUrl('http://bbh') && !Text.isUrl('ftp://bbh') && !Text.isUrl(null)
    }
}
