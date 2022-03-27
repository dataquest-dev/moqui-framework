import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import dtq.rockycube.entity.MasterEntityHandler

import spock.lang.Shared
import spock.lang.Specification

class JsonSupportTests extends Specification {
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

    // provided entity should contain JSONb in its definitions
    def "test_jsonb_ds_support"()
    {
        when:

        def ed = this.meh.getDefinition("moqui.test.TestEntity", false, "transactional")

        then:
            ed != null

    }

    // write JSONB into database and test the outcome when it's read from database
    def "store_jsonb"()
    {
        when:

        // disable authz
        ec.artifactExecution.disableAuthz()

        // create new entity
        def newStoredJson = ec.entity.makeValue("moqui.test.TestEntity")
                .setAll([
                        testJsonField:[
                                value: 1981,
                                theOtherList: [1, 2, 3]
                        ],
                ])
                .setSequencedIdPrimary()
                .create()

        // must be created
        assert newStoredJson

        // load JSON via EndpointService
        def data = ec.service.sync().name("dtq.rockycube.EndpointServices.populate#EntityData").disableAuthz().parameters([
                entityName: "moqui.test.TestEntity",
                term      : [[field:'testId', value:newStoredJson.testId]]
        ]).call() as HashMap

        then:
        data != null
    }
}
