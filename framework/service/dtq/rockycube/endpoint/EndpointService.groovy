import dtq.rockycube.endpoint.EndpointServiceHandler

def loadFromEntity()
{
    EndpointServiceHandler ech = new EndpointServiceHandler(args, term, entityName, tableName, failsafe, serviceAllowedOn)
    try {
        return ech.fetchEntityData(index, size, orderBy)
    } catch (Exception exc){
        ech.addError(500, "Failed on fetch: ${exc.message}", null)
        return null;
    }
}

def deleteEntity()
{
    EndpointServiceHandler ech = new EndpointServiceHandler(args, term, entityName, tableName, failsafe, serviceAllowedOn)
    // ec.logger.debug("Executing deleteEntity method")
    try {
        return ech.deleteEntityData()
    } catch (Exception exc){
        ech.addError(500, "Failed on delete: ${exc.message}", null)
        return null;
    }

}

def updateEntity()
{
    EndpointServiceHandler ech = new EndpointServiceHandler(args, term, entityName, tableName, failsafe, serviceAllowedOn)
    try {
        return ech.updateEntityData(data)
    } catch (Exception exc){
        ech.addError(500, "Failed on update: ${exc.message}", null)
        return null;
    }
}

def createEntity()
{
    EndpointServiceHandler ech = new EndpointServiceHandler(args, term, entityName, tableName, failsafe, serviceAllowedOn)
    try {
        return ech.createEntityData(data)
    } catch (Exception exc){
        ech.addError(500, "Failed on create: ${exc.message}", null)
        return null;
    }
}