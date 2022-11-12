import com.google.gson.Gson
import net.javacrumbs.jsonunit.JsonAssert
import net.javacrumbs.jsonunit.core.Option
import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.util.TestUtilities
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

    def test_cache_operations() {
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
