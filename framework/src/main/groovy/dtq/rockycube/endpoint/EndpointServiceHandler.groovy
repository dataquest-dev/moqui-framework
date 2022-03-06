package dtq.rockycube.endpoint

import dtq.rockycube.entity.MasterEntityHandler
import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityException
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

    EndpointServiceHandler() {
        this.ec = Moqui.getExecutionContext()
        this.ecfi = (ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()
        this.meh = new MasterEntityHandler(this.ec)
    }

    private EntityConditionImplBase extractQuery(Object term)
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

    private void manipulateRecordId(HashMap<String, Object> record, HashMap<String, Object> args, EntityDefinition ed)
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

    private void manipulateExtraFields(HashMap<String, Object> record, HashMap<String, Object> args, EntityDefinition ed)
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

    public HashMap serveEndpoint()
    {
        def entityName = (String) ec.context.entityName
        def term = (ArrayList) ec.context.term?:[]
        def inputIndex = (Integer) ec.context.index?:1
        def pageSize = (Integer) ec.context.size?:20
        def orderBy = (ArrayList) ec.context.orderBy?:[]
        ec.logger.debug("ec.context.args: ${ec.context.args}")
        HashMap<String, Object> args = (HashMap<String, Object>) ec.context.args?:[:]

        // do we have an entity
        if (!entityName) return [result: false, message: 'Missing entity name']

        // modify index
        Integer pageIndex = Math.max(inputIndex - 1, 0)

        // entity definition is a must
        EntityDefinition ed = this.meh.getDefinition(entityName, false)
        if (!ed) return [result: false, message: 'Entity definition not found, cannot continue with populating service output']

        // fill in defaults if no arguments passed
        this.checkArgsSetup(args)

        // query condition setup
        EntityCondition queryCondition = this.extractQuery(term)
        logger.debug("entityName/term/index/size: ${entityName}/${queryCondition}/${pageIndex}/${pageSize}")

        // pagination info
        def pagination = this.fetchPaginationInfo(entityName, pageIndex, pageSize, queryCondition)
        logger.debug("pagination: ${pagination}")

        def evs = ec.entity.find(entityName)
                .condition(queryCondition)
                .limit(pageSize)
                .offset(pageIndex * pageSize)

        // order by columns
        if (orderBy) evs.orderBy(orderBy)

        def res = []
        for (EntityValue ev in evs.list())
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
            this.manipulateRecordId(recordMap, args, ed)
            this.manipulateExtraFields(recordMap, args, ed)

            // add to output, sorted
            res.add(recordMap.sort({m1, m2 -> m1.key <=> m2.key}))
        }

        return [
                result: true,
                data: res,
                pagination: pagination
        ]
    }
}
