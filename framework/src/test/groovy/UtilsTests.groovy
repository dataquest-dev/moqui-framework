import org.moqui.impl.ViUtilities
import org.moqui.util.TestUtilities
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.time.LocalDate

class UtilsTests extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(UtilsTests.class)

    def setupSpec() {

    }

    def cleanupSpec() {

    }

    def test_to_LocalDate_conversion() {
        when:

        assert ViUtilities.stringToDate("2022-04-01") == LocalDate.of(2022, 4, 1)

        then:
        1 == 1
    }

    def test_comma_splitting() {
        when:

        assert ViUtilities.splitWithBracketsCheck("OR(1,AND(5,6)),AND(3,4)") == ["OR(1,AND(5,6))", "AND(3,4)"]
        assert ViUtilities.splitWithBracketsCheck("OR(1,AND(5,6)),AND(AND(11,10, OR(7,8)),4)") == ["OR(1,AND(5,6))", "AND(AND(11,10, OR(7,8)),4)"]

        then:
        1 == 1
    }

    def test_file_w_ts(){
        when:

        def ts = TestUtilities.formattedTimestamp()

        assert TestUtilities.insertBeforeExtension("test.json", ts) == "test_${ts}.json".toString()

        then:
        1 == 1
    }

}
