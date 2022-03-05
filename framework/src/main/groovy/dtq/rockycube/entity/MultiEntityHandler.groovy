package dtq.rockycube.entity

import org.moqui.context.ExecutionContext
import org.moqui.impl.entity.EntityDefinition
import org.moqui.impl.entity.EntityFacadeImpl
import org.moqui.impl.entity.EntityJavaUtil

class MultiEntityHandler {
    // contexts and facades
    private ExecutionContext ec
    private EntityFacadeImpl efi

    // EntityJavaUtil - for the sake of converting strings
    EntityJavaUtil util = new EntityJavaUtil()

    MultiEntityHandler(ExecutionContext ec){
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

        return iterator.find{ it ->
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
                String convStringValue = this.formattedString(convType, entityName)
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

        /*def foundEntity = { Map.Entry<String, EntityDefinition> it ->
            boolean strictMatch = (it.key == entityName)
            // if group name set, confirm match
            if (groupName) strictMatch &= (it.value.groupName == groupName)
            if (strictMatch) return true

            // quit if not set to loose match
            if (!looseMatch) return false

            // convert name to a camel case format
            return allowedConversions.any {convType ->
                String convStringValue = this.formattedString(convType, entityName)
                if (!convStringValue) return false
                looseMatch = (it.key.endsWith == entityName)
                if (groupName) looseMatch &= (it.value.groupName == groupName)
                return looseMatch
            }
        } as Map.Entry<String, Object>*/

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

    private String formattedString(String conversionType, String input)
    {
        switch (conversionType)
        {
            case 'underscoredToCamelCase-firstUpper':
                return this.util.underscoredToCamelCase(input, true)
                break
            default:
                return null
        }
    }
}
