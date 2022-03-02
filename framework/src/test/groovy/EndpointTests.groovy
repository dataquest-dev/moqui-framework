import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

class EndpointTests extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(EndpointTests.class)

    @Shared
    ExecutionContext ec

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
        ec.user.loginUser('john.hardy', 'moqui')
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def "test_sorting_endpoint_output"()
    {
        when:

        def enums = this.ec.service.sync()
                .name("dtq.rockycube.EndpointServices.populate#EntityData")
                .parameters([entityName: "moqui.basic.Enumeration", index: 0, size: 2])
                .call()

        then:
        enums != null
        logger.info("enums: ${enums}")
    }
}
