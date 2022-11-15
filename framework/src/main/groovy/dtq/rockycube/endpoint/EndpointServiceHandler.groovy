package dtq.rockycube.endpoint


import com.google.gson.Gson
import dtq.rockycube.cache.CacheQueryHandler
import dtq.rockycube.entity.EntityHelper
import dtq.synchro.SynchroMaster
import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityException
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.impl.ViUtilities
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.condition.ConditionField
import org.moqui.impl.entity.condition.EntityConditionImplBase
import org.moqui.impl.entity.condition.FieldValueCondition
import org.moqui.impl.entity.condition.ListCondition
import org.moqui.util.CollectionUtilities
import org.moqui.util.ObjectUtilities
import org.moqui.util.RestClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class EndpointServiceHandler {
    protected final static Logger logger = LoggerFactory.getLogger(EndpointServiceHandler.class);
    private ExecutionContext ec
    private ExecutionContextFactoryImpl ecfi
    private EntityHelper meh
    private EntityCondition.JoinOperator defaultListJoinOper = EntityCondition.JoinOperator.OR

    private static String CONST_UPDATE_IF_EXISTS                = 'updateIfExists'
    private static String CONST_ALLOWED_FIELDS                  = 'allowedFields'
    private static String CONST_CONVERT_OUTPUT_TO_LIST          = 'convertToList'
    private static String CONST_ALLOW_TIMESTAMPS                = 'allowTimestamps'
    private static String CONST_AUTO_CREATE_PKEY                = 'autoCreatePrimaryKey'
    private static String CONST_PREFER_OBJECT_IN_RETURN         = 'preferObjectInReturn'
    private static String CONST_RENAME_MAP                      = 'renameMap'
    private static String CONST_CONVERT_OUTPUT_TO_FLATMAP       = 'convertToFlatMap'
    // when using complex query builder, use this parameter for setup (e.g. AND(1, OR(2, 3)))
    private static String CONST_COMPLEX_CONDITION_RULE          = 'complexCondition'
    // intelligent-cache query feature is used by default, if you need to set this parameter to `true`
    private static String CONST_ALLOW_ICACHE_QUERY              = 'allowICacheQuery'
    private static String CONST_DEFAULT_LIST_JOIN_OPERATOR      = 'defaultListJoinOperator'

    /*
    REQUEST ATTRIBUTES
    */
    private String entityName
    private ArrayList term
    private Integer inputIndex = 1
    private Integer pageSize
    private ArrayList orderBy
    private HashMap<String, Object> args

    // variables extracted
    private EntityDefinition ed
    private EntityConditionImplBase queryCondition
    private Integer pageIndex
    private String dsType

    public static HashMap defaultErrorResponse(String message)
    {
        return [
                result: false,
                message: message
        ]
    }

    EndpointServiceHandler() {
        this.ec = Moqui.getExecutionContext()
        this.ecfi = (ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()
        this.meh = new EntityHelper(this.ec)

        // do we have an entity?
        // try extracting from table name
        if (!ec.context.entityName) {
            String tableName = ec.context.tableName ?: null
            if (!tableName) throw new EntityException("Missing both entity and table name")

            this.ed = meh.getDefinition(tableName)
            if (!ed) throw new EntityException("Unable to find EntityDefinition for '${tableName}'")
            this.entityName = ed.fullEntityName
        } else {
            this.entityName = (String) ec.context.entityName
        }

        // initial fill
        this.fillRequestVariables()

        // subsequent calculations
        this.calculateDependencies()

        def ds = ec.entity.getDatasourceFactory(ed.groupName)
        dsType = ds.getClass().simpleName
    }

    private static Object cleanMongoObjects(Object incoming)
    {
        if (incoming.getClass().simpleName == "LinkedHashMap")
        {
            def newMap = [:]
            for (def itemKey in incoming.keySet())
            {
                newMap.put(itemKey, cleanMongoObjects(incoming[itemKey]))
            }
            return newMap
        } else if (incoming.getClass().simpleName == "ArrayList")
        {
            def newList = []
            for (def item in incoming)
            {
                newList.add( cleanMongoObjects(item))
            }
            return newList
        } else if (incoming.getClass().simpleName == "Document"){
            return (LinkedHashMap) incoming
        } else {
            return incoming
        }
    }

    // rename field if necessary
    private String fieldName(String name)
    {
        // do we have a setup object?
        if (!args.containsKey(CONST_RENAME_MAP)) return name

        // find in map
        try {
            HashMap renameMap = args[CONST_RENAME_MAP] as HashMap
            return renameMap.get(name, name)
        } catch (Exception exc){
            logger.error("Invalid RENAME_MAP provided: ${exc.message}")
            return name
        }
    }

    private Object fillResultset(EntityValue single)
    {
        Gson gson = new Gson()
        HashMap<String, Object> res = [:]
        HashMap<String, Object> recordMap = [:]
        // logger.info("args.allowedFields: ${args[CONST_ALLOWED_FIELDS]}")

        single.entrySet().each { it->
            if (!it.key) return
            if (!addField(it.key)) return
            def fieldName = fieldName(it.key)

            // for MONGO DATABASE, make it simple, we do not want to much
            // of EntityDefinition handling around
            if (dsType == "MongoDatasourceFactory")
            {
                recordMap.put(fieldName, cleanMongoObjects(it.value))
                return
            }

            // value and it's class
            def itVal = it.value

            // special treatment for maps
            // convert HashMap
            if (it.isMapField())
            {
                def itValCls = it.value.getClass().simpleName.toLowerCase()
                switch (itValCls)
                {
                    case "byte[]":
                        def itStrVal = new String((byte[]) it.value, StandardCharsets.UTF_8)
                        itVal = gson.fromJson(itStrVal, HashMap.class)
                        break
                    default:
                        itVal = gson.fromJson(itVal.toString(), HashMap.class)
                }
            }

            recordMap.put(fieldName, itVal)
        }

        // handle specialities
        this.manipulateRecordId(recordMap)
        this.manipulateExtraFields(recordMap)

        // add to output, sorted
        def sortedMap = recordMap.sort({m1, m2 -> m1.key <=> m2.key})

        // change to list, if set in such way
        if (args[CONST_CONVERT_OUTPUT_TO_LIST] == true) {
            def conv2List = []
            sortedMap.each { it ->
                conv2List.push(it.value)
            }

            if (conv2List.size() == 1) {
                return conv2List[0]
            } else {
                return conv2List
            }
        } else if (args[CONST_CONVERT_OUTPUT_TO_FLATMAP] == true){
            return CollectionUtilities.flattenNestedMap(sortedMap)
        } else {
            return sortedMap
        }
    }

    private Object fillResultset(EntityList entities) {
        // return as array by default, only when set otherwise
        if (entities.size() == 1 && args[CONST_PREFER_OBJECT_IN_RETURN])
        {
            return fillResultset(entities[0])
        }

        // otherwise return as an array
        def res = []
        for (EntityValue ev in entities) {
            res.add(fillResultset(ev))
        }
        return res
    }

    private boolean addField(String fieldName)
    {
        // allow timestamps? must be explicitly set
        def timestamps = ["lastUpdatedStamp", "creationTime"]
        if (timestamps.contains(fieldName))
        {
            return args[CONST_ALLOW_TIMESTAMPS]
        }

        switch(args[CONST_ALLOWED_FIELDS].getClass().simpleName)
        {
            case 'String':
                def afld = (String) args[CONST_ALLOWED_FIELDS]
                if (afld == '*') {
                    return true
                } else if (afld == fieldName) {
                    return true
                }
                break
            case 'ArrayList':
                def aflds = (ArrayList) args[CONST_ALLOWED_FIELDS]
                if (aflds.size() == 1 && aflds[0] == "*")
                {
                    return true
                } else
                {
                    def forbiddenFields = []
                    aflds.each {it->
                        if (it.toString().startsWith('-')) forbiddenFields.add(it.toString().substring(1))
                    }

                    // forbidden fields
                    if (forbiddenFields.contains(fieldName)) return false

                    boolean allFieldsFlag = aflds.count {it->
                        return it == "*"
                    } == 1

                    if (allFieldsFlag) return true
                    if (aflds.contains(fieldName)) return true
                    return false
                }
                break
            default:
                return false
        }
        return false
    }

    private void calculateDependencies()
    {
        // fill in defaults if no arguments passed
        this.checkArgsSetup()

        // query condition setup
        this.queryCondition = this.extractQueryCondition(term)

        // modify index
        this.pageIndex = Math.max(inputIndex - 1, 0)
        logger.info("entityName/term/index/size: ${entityName}/${queryCondition}/${pageIndex}/${pageSize}")

        // entity definition is a must
        if (!this.ed) this.ed = this.meh.getDefinition(entityName)
        if (!this.ed) throw new EntityException("Entity definition not found [${entityName?:'NOT SET'}], cannot continue with populating service output")
    }

    private void fillRequestVariables()
    {
        this.term = (ArrayList) ec.context.term?:[]
        this.inputIndex = (Integer) ec.context.index?:1
        this.pageSize = (Integer) ec.context.size?:20
        this.orderBy = (ArrayList) ec.context.orderBy?:[]
        this.args = ec.context.args?:[:] as HashMap<String, Object>
    }

    // good old recursion once again
    // we need to process special string and return complex condition out of it
    private EntityConditionImplBase recCondition(String ruleIn, ArrayList term)
    {
        // if it's simple component, a number, return baseCondition
        def recSingle = Pattern.compile("^(\\d)\$")
        def mSingle = recSingle.matcher(ruleIn)
        if (mSingle)
        {
            def groupNum = mSingle.group(1).toString().toInteger()
            HashMap<String, Object> fieldCond = term.get(groupNum - 1)
            return getSingleFieldCondition(fieldCond)
        }

        // use regex to search for both OPERATOR and CONTENT itself
        def rec = Pattern.compile("^(AND|OR)\\((.+)\\)")
        def m = rec.matcher(ruleIn)
        boolean isEntireListCond = m.matches()
        // 1. if string matches pattern from above, then it's the entire condition
        // 2. if not, than we may have a list in place
        if (!isEntireListCond)
        {
            // then, split them using comma and return one by one
            def items = ViUtilities.splitWithBracketsCheck(ruleIn, ",")

            List<EntityCondition> res = new ArrayList()
            items.each {it->
                res.add(recCondition(it, term))
            }
            return res as EntityConditionImplBase
        }

        def joinOp = EntityCondition.JoinOperator.AND
        switch (m.group(1))
        {
            case "OR":
                joinOp = EntityCondition.JoinOperator.OR
                break
        }
        def entireCond = new ListCondition(recCondition(m.group(2), term), joinOp)
        return entireCond
    }

    private EntityConditionImplBase extractComplexCondition(Object term, String rule)
    {
        try {
            return recCondition(rule, term as ArrayList)
        } catch(Error err) {
            logger.error(err.message)
            throw new EndpointException("Invalid condition construction, possible StackOverflow error")
        } catch(Exception exc) {
            logger.error(exc.message)
            throw new EndpointException("Invalid condition construction")
        }
    }

    private static FieldValueCondition getSingleFieldCondition(HashMap<String, Object> singleTerm)
    {
        // check required fields
        if (!singleTerm.containsKey("field")) throw new EntityException("Field condition not set correctly, 'field' missing [${singleTerm}]")
        if (!singleTerm.containsKey("value")) throw new EntityException("Field condition not set correctly, 'value' missing [${singleTerm}]")

        def compOperator = EntityCondition.ComparisonOperator.EQUALS

        // logger.debug("singleTerm.value.getClass() = ${singleTerm.value.getClass().simpleName}")
        if (singleTerm.containsKey("operator"))
        {
            switch(singleTerm["operator"].toString().toLowerCase())
            {
                case "=":
                case "eq":
                case "equal":
                case "equals":
                    // do nothing
                    break
                case "!=":
                case "not-equal":
                    compOperator = EntityCondition.ComparisonOperator.NOT_EQUAL
                    break
                case ">":
                case "gt":
                    compOperator = EntityCondition.ComparisonOperator.GREATER_THAN
                    break
                case ">=":
                case "gte":
                    compOperator = EntityCondition.ComparisonOperator.GREATER_THAN_EQUAL_TO
                    break
                case "<":
                case "lt":
                    compOperator = EntityCondition.ComparisonOperator.LESS_THAN
                    break
                case "<=":
                case "lte":
                    compOperator = EntityCondition.ComparisonOperator.LESS_THAN_EQUAL_TO
                    break
                case "like":
                    compOperator = EntityCondition.ComparisonOperator.LIKE
                    break
                case "not-like":
                    compOperator = EntityCondition.ComparisonOperator.NOT_LIKE
                    break
                case "in":
                    compOperator = EntityCondition.ComparisonOperator.IN
                    if (singleTerm.value.getClass().simpleName != "ArrayList") throw new EntityException("Operator requires List value, but was not provided")
                    break
                case "not-in":
                    compOperator = EntityCondition.ComparisonOperator.NOT_IN
                    if (singleTerm.value.getClass().simpleName != "ArrayList") throw new EntityException("Operator requires List value, but was not provided")
                    break
                case "between":
                    compOperator = EntityCondition.ComparisonOperator.BETWEEN
                    if (singleTerm.value.getClass().simpleName != "ArrayList") throw new EntityException("Operator requires List value, but was not provided")
                    if (singleTerm.value.size() != 2) throw new EntityException("Operator requires exactly two values in array")
                    break
                case "not-between":
                    compOperator = EntityCondition.ComparisonOperator.NOT_BETWEEN
                    if (singleTerm.value.getClass().simpleName != "ArrayList") throw new EntityException("Operator requires List value, but was not provided")
                    if (singleTerm.value.size() != 2) throw new EntityException("Operator requires exactly two values in array")
                    break
                case "text":
                    compOperator = EntityCondition.ComparisonOperator.TEXT
                    break
            }
        }

        return new FieldValueCondition(
                new ConditionField((String) singleTerm.field),
                compOperator,
                (Object) singleTerm.value
        )
    }

    private EntityConditionImplBase extractQueryCondition(Object term)
    {
        // no Strings as term
        if (term.getClass().simpleName == "String") throw new EntityException("Unsupported type for Term: String")

        // should there be an argument specifying how the complex condition
        // must be set, let's do it this way
        if (this.args.containsKey(CONST_COMPLEX_CONDITION_RULE)) {
            return this.extractComplexCondition(term, this.args[CONST_COMPLEX_CONDITION_RULE] as String)
        }

        // otherwise make it simple and add `OR` between all conditions
        def resListCondition = []
        for (HashMap<String, Object> singleTerm in term) resListCondition.add(getSingleFieldCondition(singleTerm))
        return new ListCondition(resListCondition, this.defaultListJoinOper)
    }

    private HashMap fetchPaginationInfo(
            String entityName,
            Integer pageIndex,
            Integer pageSize,
            EntityCondition queryCondition)
    {
        def res = [:]
        def allEntries = ec.entity.find(entityName)
                .condition(queryCondition)
                .count()

        res['size'] = pageSize
        res['page'] = pageIndex + 1
        res['rows'] = allEntries
        return res
    }

    /**
     * Fills arguments with some defaults, should it be necessary
     * @param args
     */
    private void checkArgsSetup()
    {
        // by default
        //      do not overwrite
        if (!args.containsKey(CONST_UPDATE_IF_EXISTS)) args.put(CONST_UPDATE_IF_EXISTS, false)

        //      all fields are allowed
        if (!args.containsKey(CONST_ALLOWED_FIELDS)) args.put(CONST_ALLOWED_FIELDS, '*')

        //      we do not want list as output
        if (!args.containsKey(CONST_CONVERT_OUTPUT_TO_LIST)) args.put(CONST_CONVERT_OUTPUT_TO_LIST, false)

        //      we do not want timestamp fields, by default
        if (!args.containsKey(CONST_ALLOW_TIMESTAMPS)) args.put(CONST_ALLOW_TIMESTAMPS, false)

        //      let the entity manager create primary key
        if (!args.containsKey(CONST_AUTO_CREATE_PKEY)) args.put(CONST_AUTO_CREATE_PKEY, true)

        //      do not attempt to force-return an object
        if (!args.containsKey(CONST_PREFER_OBJECT_IN_RETURN)) args.put(CONST_PREFER_OBJECT_IN_RETURN, false)

        //      use iCache query only when explicitly set
        if (!args.containsKey(CONST_ALLOW_ICACHE_QUERY)) args.put(CONST_ALLOW_ICACHE_QUERY, false)

        //      default join operator change
        if (args.containsKey(CONST_DEFAULT_LIST_JOIN_OPERATOR))
        {
            switch(args[CONST_DEFAULT_LIST_JOIN_OPERATOR].toString().toLowerCase())
            {
                case "and":
                case "&&":
                    this.defaultListJoinOper = EntityCondition.JoinOperator.AND
                    break
                case "or":
                case "||":
                    this.defaultListJoinOper = EntityCondition.JoinOperator.OR
            }
        }
    }

    private void manipulateRecordId(HashMap<String, Object> record)
    {
        if (!args.get('renameId', null)) return

        // modify ID to a new value
        def newIdField = args.get('renameId').toString()

        // make sure it's a unique name
        Number occurrences = record.count {it->
            return newIdField == it.key
        }

        if (occurrences != 0)
        {
            logger.error("Field exist, cannot be renamed")
            return
        }

        def idValue = null
        def idFieldName = null
        record.each {it->
            if (!ed.isPkField(it.key, true)) return
            idValue = it.value
            idFieldName = it.key
        }

        if (!idValue || !idFieldName)
        {
            logger.error("Unable to extract ID field or ID field value")
            return
        }

        record[newIdField] = idValue
        record.remove(idFieldName)
    }

    private void manipulateExtraFields(HashMap<String, Object> record)
    {
        if (!args.get('removeFields', null)) return

        def fields2remove = (ArrayList) args['removeFields']
        fields2remove.each {fld->
            if (ed.isPkField(fld)) return
            if (!record.containsKey(fld)) return

            // remove key
            record.remove(fld)
        }
    }

    public HashMap createEntityData()
    {
        def data = ec.context.data
        // ArrayList supported, this way we can run multiple create procedures in single moment
        // if (data.getClass().simpleName.startsWith('ArrayList')) throw new EntityException("Creating multiple entities not supported")

        def itemsCreated = 0
        HashMap<String, Object> lastItemResult = [:]

        // different for array and hashmap
        if (data instanceof HashMap || data instanceof LinkedHashMap) {
            lastItemResult = this.createSingleEntity(data as HashMap)
            return lastItemResult

        } else {
            data.each { HashMap it ->
                lastItemResult = this.createSingleEntity(it)
                if (lastItemResult['result']) itemsCreated += 1
            }
        }

        // if only one, return last item's result
        if (itemsCreated == 1) return lastItemResult

        return [
                result : true,
                message: "Records created/updated (${itemsCreated})"
        ]
    }

    private HashMap createSingleEntity(HashMap singleEntityData) {
        if (singleEntityData.isEmpty()) {
            // return empty map
            return defaultErrorResponse("Single entity creation failed, no data provided")
        }

        // update if necessary
        // otherwise perform clean write
        if (args.get(CONST_UPDATE_IF_EXISTS) && queryCondition)
        {
            def allreadyExists = ec.entity.find(entityName).condition(queryCondition)
            if (allreadyExists.count() == 1)
            {
                return this.updateSingleEntity(allreadyExists, singleEntityData)
            } else if (allreadyExists.count() > 1)
            {
                return defaultErrorResponse("Unable to perform create/update, multiple entries exist")
            }
        }

        def created = ec.entity.makeValue(entityName)
                .setAll(singleEntityData)

        // create primary key
        if (args[CONST_AUTO_CREATE_PKEY] == true) {
            created.setSequencedIdPrimary()
        }

        // let it create
        created.create()

        // get result back to the caller
        def res = [
                result : true,
                message: "Records created (1)",
                data   : [fillResultset(created)]
        ]
    }

    public HashMap deleteEntityData()
    {
        def toDeleteSearch = ec.entity.find(entityName).condition(queryCondition)
        logger.debug("DELETE: entityName/term: ${entityName}/${queryCondition}")

        // convert to list for message
        def toDelete = toDeleteSearch.list()

        // if no records deleted, quit, with false flag
        if (toDelete.size() == 0)
        {
            return [
                    result: false,
                    message: "No records to delete were found"
            ]
        }

        for (EntityValue ev in toDeleteSearch)
        {
            // delete
            ev.delete()
        }

        // store items being deleted
        def deleted = this.fillResultset(toDelete)

        return [
                result: true,
                message: "Records deleted (${deleted.size()})",
                data: deleted
        ]
    }

    // fetch Entity data using `standard entity model`
    private HashMap fetchEntityData_standard()
    {
        // pagination info
        def pagination = this.fetchPaginationInfo(entityName, pageIndex, pageSize, queryCondition)
        logger.debug("pagination: ${pagination}")

        def evs = ec.entity.find(entityName)
                .condition(queryCondition)
                .limit(pageSize)
                .offset(pageIndex * pageSize)

        // order by columns
        if (orderBy) evs.orderBy(orderBy)

        // update pagination info, so that count of rows being displayed is returned
        def result = evs.list()
        pagination['displayed'] = result.size()

        return [
                result: true,
                data: this.fillResultset(result),
                pagination: pagination
        ]
    }

    // fetch Entity data using `intelligent cache model`
    public HashMap fetchCachedData(SynchroMaster syncMaster)
    {
        // is there a cache?
        if (!syncMaster.getEntityIsSynced(this.entityName)) throw new EndpointException("Entity is not synced in a cache")


        // features not supported
        // 1. complex queries
        if (args[CONST_COMPLEX_CONDITION_RULE]) throw new EndpointException("Complex queries not supported for i-cache")

        // 2. pagination not supported
        if (this.inputIndex > 1) throw new EndpointException("Pagination not supported for i-cache queries")

        // populate using query handler
        def qh = new CacheQueryHandler()


        return [
                result: true,
                data: [:]
        ]
    }

    public HashMap fetchEntityData()
    {
        // only available when specific flag is switched
        if (args[CONST_ALLOW_ICACHE_QUERY]) {
            // SynchroMaster required
            SynchroMaster syncMaster = null
            try {
                syncMaster = ec.getTool("SynchroMaster", SynchroMaster.class)
            } catch (Exception exc) {
                logger.error("Unable to use initialize SynchroMaster for cache-based data loading [${exc.message}]")
            }

            // if tool is not ready, throw exception, we expect it works
            if (!syncMaster) throw new EndpointException("Unable to perform cache-based data loading, not cache handler")

            return this.fetchCachedData(syncMaster)
        }

        // standard way
        return this.fetchEntityData_standard()
    }

    public HashMap updateEntityData()
    {
        HashMap<String, Object> updateData = ec.context.data?:[:]
        if (updateData.isEmpty())
        {
            return [
                    result: false,
                    message: 'No data for update found'
            ]
        }

        def toUpdate = ec.entity.find(entityName)
                .condition(queryCondition)
                .forUpdate(true)
        logger.debug("UPDATE: entityName/term: ${entityName}/${queryCondition}")

        // update record
        return this.updateSingleEntity(toUpdate, updateData)
    }

    private HashMap updateSingleEntity(EntityFind toUpdate, HashMap<String, Object> updateData){
        // if no records deleted, quit, with false flag
        if (toUpdate.count() == 0)
        {
            return [
                    result: false,
                    message: "No record to update was found"
            ]
        }

        // allow only single record update
        if (toUpdate.count() > 1)
        {
            return [
                    result: false,
                    message: "Multiple records to update were found"
            ]
        }

        def mod = toUpdate.one()

        // set new values
        updateData.each {it->
            mod.set((String) it.key, it.value)
        }

        // save
        mod.update()

        return [
                result : true,
                message: "Records updated (1)",
                data   : [fillResultset(mod)]
        ]
    }

    public static void sendRenderedResponse(String entityName, ArrayList term, LinkedHashMap args, String templateName, boolean inline) {
        // add args
        if (!args.containsKey('preferObjectInReturn')) args['preferObjectInReturn'] = true

        // initialize EC
        def ec = Moqui.getExecutionContext()

        try {
            // 1. load data, use endpoint handler (and terms) to locate data
            def reportData = ec.service.sync().name("dtq.rockycube.EndpointServices.populate#EntityData").parameters([
                    entityName: entityName,
                    term      : term,
                    args      : args
            ]).call() as HashMap

            // check result
            if (!reportData) throw new EntityException("Unable to retrieve data for rendering response")
            if (!reportData.containsKey('data')) throw new EntityException("No data in response, cannot proceed with template rendering")

            def dataToProcess = reportData['data']

            // 2. transfer data to PY-CALC to get it rendered
            // by writing into response's output stream
            InputStream renderedTemplate = renderTemplate(templateName, dataToProcess)
            def response = ec.web.response

            try {
                OutputStream os = response.outputStream
                try {
                    int totalLen = ObjectUtilities.copyStream(renderedTemplate, os)
                    logger.info("Streamed ${totalLen} bytes from response")
                } finally {
                    os.close()
                }
            } finally {
                // close stream
                renderedTemplate.close()
            }

            // 3. return response back to frontend as inline content
            if (inline) response.addHeader("Content-Disposition", "inline")

            // set content type
            response.setContentType("text/html")
            response.setCharacterEncoding("UTF-8")

        } catch (Exception exc) {
            ec.logger.error(exc.message)
            ec.web.response.sendError(400, "Unable to generate template")
        }
    }

    // render data in PY-CALC using template
    public static InputStream renderTemplate(String template, Object data) {
        ExecutionContext ec = Moqui.getExecutionContext()

        // expect config in system variables
        def pycalcHost = System.properties.get("py.server.host")
        def renderTemplateEndpoint = System.properties.get("py.endpoint.render")

        // basic checks
        if (!pycalcHost) throw new EndpointException("PY-CALC server host not defined")
        if (!renderTemplateEndpoint) throw new EndpointException("PY-CALC server's render template endpoint not set")

        RestClient restClient = ec.service.rest().method(RestClient.POST)
                .uri("${pycalcHost}/${renderTemplateEndpoint}")
                .addHeader("Content-Type", "application/json")
                .addBodyParameter("template", template)
                .jsonObject(data)
        RestClient.RestResponse restResponse = restClient.call()

        // check status code
        if (restResponse.statusCode != 200) {
            throw new EndpointException("Response with status ${restResponse.statusCode} returned: ${restResponse.reasonPhrase}")
        }

        return new ByteArrayInputStream(restResponse.bytes());
    }
}