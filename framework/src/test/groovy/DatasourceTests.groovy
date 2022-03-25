import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import dtq.rockycube.entity.MasterEntityHandler

import spock.lang.Shared
import spock.lang.Specification

class DatasourceTests extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(EndpointTests.class)

    @Shared
    ExecutionContext ec

    @Shared
    MasterEntityHandler meh

    def setupSpec() {
        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
        ec.user.loginUser('john.doe', 'moqui')

        // init entity handler
        this.meh = new MasterEntityHandler(ec)
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def "test_jsonb_ds_existence"()
    {
        when:

        def ed = this.meh.getDefinition("moqui.test.TestEntity", false, "transactional")

        then:
            ed != null

    }
}
