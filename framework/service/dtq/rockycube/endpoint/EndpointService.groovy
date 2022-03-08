import dtq.rockycube.endpoint.EndpointServiceHandler

def loadFromEntity()
{
    EndpointServiceHandler ech = new EndpointServiceHandler()
    ec.logger.debug("Executing loadFromEntity method")
    return ech.fetchEntityData()
}

def deleteEntity()
{
    EndpointServiceHandler ech = new EndpointServiceHandler()
    ec.logger.debug("Executing deleteEntity method")
    return ech.deleteEntityData()
}

