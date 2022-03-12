package dtq.rockycube.entity

import org.moqui.context.ExecutionContext
import org.moqui.impl.ViUtilities
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityFacadeImpl

class MasterEntityHandler {
    // contexts and facades
    private ExecutionContext ec
    private EntityFacadeImpl efi

    MasterEntityHandler(ExecutionContext ec){
        this.ec = ec

        // EntityFacadeImpl
        efi = (EntityFacadeImpl) ec.getEntity()
    }

    private <K, V> Map.Entry<K, V> foundEntity(
            Iterator iterator,
            String entityName,
            String groupName,
            ArrayList<String> allowedConversions,
            boolean looseMatch) {

        ec.logger.info("Iterator class: ${iterator.getClass().simpleName}")
        ec.logger.info("Iterator size: ${iterator.size()}")


        return iterator.find{ it ->
            ec.logger.info("Current iterator: ${it} [${it.getClass().simpleName}]")
            if (!it) return

            String actualKey = it.key
            EntityDefinition actualEd = (EntityDefinition) it.value
            boolean strictMatch = (actualKey == entityName)
            // if group name set, confirm match
            if (groupName) strictMatch &= (actualEd.groupName == groupName)
            if (strictMatch) return true

            // quit if not set to loose match
            if (!looseMatch) return false

            // convert name to a camel case format
            return allowedConversions.any { convType ->
                String convStringValue = ViUtilities.formattedString(convType, entityName)
                if (!convStringValue) return false
                boolean matches = actualKey.endsWith( entityName)
                if (groupName) matches &= (actualEd.groupName == groupName)
                return matches
            }
        } as Map.Entry<K, V>

    }

    public EntityDefinition getDefinition(String entityName, boolean looseMatch = false, String groupName = null)
    {
        def allowedConversions =['underscoredToCamelCase-firstUpper']

        // 1. search among framework entities
        Map.Entry<String, Object> foundEntityDef = foundEntity(
                efi.frameworkEntityDefinitions.iterator(),
                entityName,
                groupName,
                allowedConversions,
                looseMatch)

        if (!foundEntityDef) {
            // 2. search in normal entities
            foundEntityDef = foundEntity(
                    efi.entityDefinitionCache.iterator(),
                    entityName,
                    groupName,
                    allowedConversions,
                    true)
        }

        if (!foundEntityDef) return null
        return foundEntityDef.value as EntityDefinition
    }
}
