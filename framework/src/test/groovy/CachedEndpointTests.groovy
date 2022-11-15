import com.google.gson.Gson
import dtq.synchro.SynchroMaster
import org.moqui.Moqui
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityException
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.util.TestUtilities
import org.moqui.context.ExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class CachedEndpointTests extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(CachedEndpointTests.class)
    protected Gson gson

    @Shared
    ExecutionContext ec

    def setup() {
        // initialize Gson
        this.gson = new Gson()
    }

    def setupSpec() {
        // set other conf
        System.setProperty("moqui.conf", "conf/test/CacheTestConf.xml")

        // init the framework, get the ec
        ec = Moqui.getExecutionContext()
        ec.user.loginUser('john.hardy', 'moqui')
    }

    def cleanupSpec() {
        ec.destroy()
    }

    // testing endpoint data loading
    def test_cache_extraction() {
        when:

        EntityFacadeImpl efi = (EntityFacadeImpl) ec.entity

        def entityName = "moqui.test.TestEntity"
        def cntBeforeImport = ec.entity.find(entityName).count()

        // 0. erase before
        def condCreated = ec.entity.conditionFactory.makeCondition("testMedium", EntityCondition.ComparisonOperator.LIKE, "proj_%")
        ec.entity.find(entityName).condition(condCreated).deleteAll()

        // 1. load data into entity
        // disable auto cache-refresh SynchroMaster before importing
        def tool = ec.getTool("SynchroMaster", SynchroMaster.class)
        def testCache = tool.getEntityCache(entityName)
        tool.disableSynchronization(entityName)

        def imported = this.importTestData(entityName)
        def cntAfterImport = ec.entity.find(entityName).count()
        assert imported == tool.getMissedSyncCounter(entityName)
        assert testCache.size() + imported == cntAfterImport

        // 2. synchronize
        tool.enableSynchronization(entityName)
        assert testCache.size() == cntAfterImport

        // 3. perform few queries with different conditions set
        TestUtilities.testSingleFile(
                ["CacheRelatedTests", "expected_cache_queries.json"] as String[],
                { Object processed, Object expected ->
                    def ntt = processed[0]
                    def term = processed[1]
                    def args = processed[2]

                    // it is perfectly fine to expect exception to be thrown around here
                    def pageIndex = 1
                    if (processed.size() >=4 ) pageIndex = processed[3]

                    def reportData = ec.service.sync().name("dtq.rockycube.EndpointServices.populate#EntityData").parameters([
                            entityName: ntt,
                            term      : term,
                            args      : args,
                            index     : pageIndex
                    ]).call() as HashMap

                    // exception is not throw, message is stored
                    def excMessage = ec.message.errors[0]

                    // what is expected?
                    // is it an exception or are we testing return value
                    if (excMessage)
                    {
                        assert expected[0] == excMessage

                        // cleanup errors
                        ec.message.clearErrors()
                    } else {
                        logger.info("Result: ${reportData.result}")
                        logger.info("Result.data: ${reportData.data}")
                        logger.info("Expected: ${expected}")

                        // expected value can be a list, as well as a primitive
                        assert expected == reportData.data
                    }
                }
        )

        // 4. delete at the end
        tool.disableSynchronization(entityName)
        def deleted = ec.entity.find(entityName).condition(condCreated).deleteAll()
        def cntAfterDelete = ec.entity.find(entityName).count()

        assert deleted == imported
        assert cntAfterDelete == cntAfterImport - deleted
        assert tool.getMissedSyncCounter(entityName) == deleted

        // check sizes after sync being back online
        tool.enableSynchronization(entityName)
        assert testCache.size() == cntAfterDelete

        then:
        1 == 1
    }

    // load into TestEntity from a CSV file
    // why CSV? CSV can be processed more easily hand-generated than JSON
    private Long importTestData(String entityName)
    {
        Long created = 0

        TestUtilities.readFileLines(["CacheRelatedTests", "import-batch.csv"] as String[], ";", { String[] values ->
//            logger.info("Values: ${values.join("+")}")

            // use entity model to create values
            def newNtt = ec.entity.makeValue(entityName).setAll([
                    testMedium:values[0],
                    testNumberInteger:values[1].toInteger(),
                    testNumberDecimal:values[2].toDouble(),
                    testNumberFloat:values[3].toFloat()
            ])
                    .setSequencedIdPrimary().create()

            // increment counter
            if (newNtt) created += 1
        })

        return created
    }

    // basic manipulation with entities and their effect on cache
    def test_basic_cache_operations() {
        when:

        // declared once
        String testDir = "CachedTests"
        String testedEntity = "moqui.test.TestEntity"

        def testCache = ec.cache.getCache("i.cache.${testedEntity}")

        // 1. how many entities are there right now?
        // must be the same as in the cache
        def testNtts = ec.entity.find(testedEntity).list()
        assert testNtts.size() == testCache.size()

        // 2. create new entity
        def newId = ec.entity.makeValue(testedEntity)
                .setAll([testMedium: '1'])
                .setSequencedIdPrimary()
                .create()
        assert testCache.size() == testNtts.size() + 1

        // 3. update
        def nttToUpdate = ec.entity.find(testedEntity).condition("testId", newId.testId).forUpdate(true).one()
        nttToUpdate.set("testMedium", "text should be here").update()
        def updated = testCache.get(newId.testId)
        assert updated.testMedium == "text should be here"

        // 4. delete
        def nttToDelete = ec.entity.find(testedEntity).condition("testId", newId.testId).deleteAll()
        assert nttToDelete == 1
        assert testNtts.size() == testCache.size()

        then:
        true
    }
}
