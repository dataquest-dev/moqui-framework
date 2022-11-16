package dtq.rockycube.cache

import dtq.rockycube.entity.ConditionHandler
import org.moqui.entity.EntityCondition
import org.moqui.impl.entity.EntityDefinition

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
                matches = conditions.every {c-> return ConditionHandler.evaluateCondition(c, item.value) }
            } else {
                matches = conditions.any {c-> return ConditionHandler.evaluateCondition(c, item.value) }
            }

            if (matches) res.add(item.key)
        }

        // sort IDs
        return res.sort()
    }
}
