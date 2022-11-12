package dtq.synchro

import org.moqui.context.CacheFacade
import org.moqui.context.ExecutionContextFactory
import org.moqui.entity.EntityValue
import org.moqui.context.ExecutionContext
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityFacadeImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SynchroMaster {
    protected final Logger logger = LoggerFactory.getLogger(SynchroMaster.class)
    protected ExecutionContextFactory ecf
    protected EntityFacadeImpl efi

    // list of caches the master is taking care of
    protected ArrayList<String> syncedCaches = new ArrayList<>()

    // map of caches and related entities
    protected HashMap<String, ArrayList<String>> syncedEntities = new HashMap<>()

    SynchroMaster(ExecutionContextFactory ecf, ArrayList<String> cachesSetup)
    {
        this.ecf = ecf
        this.efi = (EntityFacadeImpl) ecf.entity

        // process each item in configuration list
        cachesSetup.each {it->
            def sourceEntity = it as String
            def cacheName = "i.cache.${it}"

            def cache = ecf.executionContext.cache.getCache(cacheName)
            def ed = efi.getEntityDefinition(sourceEntity)
            if (!checkEntityKeys(ed)) return

            def itemsToUpload = ecf.entity.find(sourceEntity).list()
            for (def i in itemsToUpload)
            {
                cache.put(i.get(ed.pkFieldNames[0]), i)
            }

            // add cache name to list
            this.syncedCaches.add(cacheName)
            // if we have entity in our map, extend the list
            if (syncedEntities.containsKey(sourceEntity))
            {
                def existingCaches = syncedEntities.get(sourceEntity)
                existingCaches.add(cacheName)
                syncedEntities.replace(sourceEntity, existingCaches)
            } else {
                syncedEntities.put(sourceEntity, [cacheName])
            }
        }
    }

    private boolean checkEntityKeys(EntityDefinition ed)
    {
        // checks in-place
        if (!ed) {logger.error("Entity not found, SynchroMaster cannot refresh cache"); return false}
        if (ed.pkFieldNames.empty) {logger.error("Entity has not primary field key, SynchroMaster cannot refresh cache"); return false}
        if (ed.pkFieldNames.size() != 1) {logger.warn("Entities with more primary keys are not supported with SynchroMaster"); return false}
        return true
    }

    public void reactToChange(String operationType, String entityName, Object recordId)
    {
        logger.debug("Performing reaction to change [${operationType}] of object [${recordId}] in entity [${entityName}]")

        if (!syncedEntities.containsKey(entityName)) {
            logger.info("Entity is not being administered by SynchroMaster")
            return
        }

        // refresh caches
        def caches = syncedEntities.get(entityName)
        assert caches.size() > 0

        // entity definition required, we need PK
        def ed = efi.getEntityDefinition(entityName)

        // primary key
        def pk = "${ed.pkFieldNames[0]}".toString()
        caches.each {
            def ch = ecf.executionContext.cache.getCache(it)
            switch (operationType){
                case "create":
                case "update":
                    def newItem = ecf.executionContext.entity.find(entityName)
                            .condition(pk, recordId)
                            .one()
                    if (operationType == "create") {ch.put(newItem.get(pk), newItem)}
                    if (operationType == "update") {ch.replace(newItem.get(pk), newItem)}
                    break
                case "delete":
                    ch.remove(recordId)
                    break
            }
        }
    }
}
