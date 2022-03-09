package dtq.rockycube.endpoint

import dtq.rockycube.entity.MasterEntityHandler
import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityException
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.condition.ConditionField
import org.moqui.impl.entity.condition.EntityConditionImplBase
import org.moqui.impl.entity.condition.FieldValueCondition
import org.moqui.impl.entity.condition.ListCondition
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EndpointServiceHandler {
    protected final static Logger logger = LoggerFactory.getLogger(EndpointServiceHandler.class);
    private ExecutionContext ec
    private ExecutionContextFactoryImpl ecfi
    private MasterEntityHandler meh

    private static String CONST_ALLOWED_FIELDS = 'allowedFields'

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

    EndpointServiceHandler() {
        this.ec = Moqui.getExecutionContext()
        this.ecfi = (ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()
        this.meh = new MasterEntityHandler(this.ec)

        // initial fill
        this.fillRequestVariables()

        // do we have an entity?
        if (!entityName) throw new EntityException("Missing entity name")

        // subsequent calculations
        this.calculateDependencies()
    }

    private ArrayList fillResultset(EntityList entities)
    {
        def res = []
        for (EntityValue ev in entities)
        {
            HashMap<String, Object> recordMap = [:]
            // logger.info("args.allowedFields: ${args[CONST_ALLOWED_FIELDS]}")

            ev.entrySet().each {it->
                switch(args[CONST_ALLOWED_FIELDS].getClass().simpleName)
                {
                    case 'String':
                        def afld = (String) args[CONST_ALLOWED_FIELDS]
                        if (afld == '*') recordMap.put(it.key, it.value)
                        if (afld == it.key) recordMap.put(it.key, it.value)
                        break
                    case 'ArrayList':
                        def aflds = (ArrayList) args[CONST_ALLOWED_FIELDS]
                        if (aflds.contains(it.key)) recordMap.put(it.key, it.value)
                        break
                    default:
                        // do nothing
                        break
                }
            }

            // handle specialities
            this.manipulateRecordId(recordMap)
            this.manipulateExtraFields(recordMap)

            // add to output, sorted
            res.add(recordMap.sort({m1, m2 -> m1.key <=> m2.key}))
        }

        return res
    }

    private void calculateDependencies()
    {
        // fill in defaults if no arguments passed
        this.checkArgsSetup(args)

        // query condition setup
        this.queryCondition = this.extractQueryCondition(term)
        logger.debug("entityName/term/index/size: ${entityName}/${queryCondition}/${pageIndex}/${pageSize}")

        // modify index
        this.pageIndex = Math.max(inputIndex - 1, 0)

        // entity definition is a must
        this.ed = this.meh.getDefinition(entityName, false)
        if (!this.ed) throw new EntityException('Entity definition not found, cannot continue with populating service output')
    }

    private void fillRequestVariables()
    {
        this.entityName = (String) ec.context.entityName
        this.term = (ArrayList) ec.context.term?:[]
        this.inputIndex = (Integer) ec.context.index?:1
        this.pageSize = (Integer) ec.context.size?:20
        this.orderBy = (ArrayList) ec.context.orderBy?:[]
        this.args = ec.context.args?:[:] as HashMap<String, Object>
    }

    private EntityConditionImplBase extractQueryCondition(Object term)
    {
        // no Strings as term
        if (term.getClass().simpleName == "String") throw new EntityException("Unsupported type for Term: String")

        def resListCondition = []
        for (def singleTerm in term) {
            resListCondition.add(
                    new FieldValueCondition(
                            new ConditionField((String) singleTerm.field),
                            EntityCondition.ComparisonOperator.EQUALS,
                            (Object) singleTerm.value
                    )
            )
        }
        return new ListCondition(resListCondition, EntityCondition.JoinOperator.OR)
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
    private void checkArgsSetup(HashMap<String, Object> args)
    {
        // by default, all fields are allowed
        if (!args.containsKey(CONST_ALLOWED_FIELDS)) args.put(CONST_ALLOWED_FIELDS, '*')
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

    public HashMap deleteEntityData()
    {
        def toDeleteSearch = ec.entity.find(entityName).condition(queryCondition)
        logger.debug("DELETE: entityName/term: ${entityName}/${queryCondition}")

        // convert to list
        def toDelete = toDeleteSearch.list()

        // if no records deleted, quit, with false flag
        if (toDelete.size() == 0)
        {
            return [
                    result: false,
                    message: "No records to delete were found"
            ]
        }

        // store items being deleted
        def deleted = this.fillResultset(toDelete)

        return [
                result: true,
                message: "Records deleted (${deleted.size()})",
                data: deleted
        ]
    }

    public HashMap fetchEntityData()
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

        return [
                result: true,
                data: this.fillResultset(evs.list()),
                pagination: pagination
        ]
    }
}
