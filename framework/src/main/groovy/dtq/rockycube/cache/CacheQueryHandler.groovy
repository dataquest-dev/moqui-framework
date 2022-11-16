package dtq.rockycube.cache

import dtq.rockycube.entity.ConditionHandler
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityException
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.condition.FieldValueCondition

import javax.cache.Cache

class CacheQueryHandler {
    protected Cache internalCache
    protected EntityDefinition entityDefinition

    CacheQueryHandler(Cache cache, EntityDefinition ed){
        this.internalCache = cache
        this.entityDefinition = ed
    }

    public ArrayList<String> fetch(EntityCondition.JoinOperator joinOperator, ArrayList<HashMap> term){
        def conditions = ConditionHandler.getFieldsCondition(term)
        ArrayList res = new ArrayList()

        // evaluate each condition
        // with each next run taking into account the previous one

        for (def item in this.internalCache)
        {
            boolean matches
            if (joinOperator == EntityCondition.JoinOperator.AND)
            {
                matches = conditions.every {c-> return evaluateCondition(c, item.value) }
            } else {
                matches = conditions.any {c-> return evaluateCondition(c, item.value) }
            }

            if (matches) res.add(item.key)
        }

        return res.sort()
    }

    public static boolean evaluateCondition(FieldValueCondition cond, Object item){
        def val = item.getAt(cond.fieldName)
        def condVal = cond.value

        switch (cond.operator){
            case EntityCondition.ComparisonOperator.EQUALS:
                return val == condVal
                break
            case EntityCondition.ComparisonOperator.NOT_EQUAL:
                return val != condVal
                break
            default:
                throw new EntityException("Operator [${cond.operator}] not supported when evaluating condition")
        }
    }
}
