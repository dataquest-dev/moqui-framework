package dtq.rockycube.endpoint

import dtq.rockycube.entity.MultiEntityHandler
import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.entity.EntityDefinition
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EndpointServiceHandler {
    protected final static Logger logger = LoggerFactory.getLogger(EndpointServiceHandler.class);
    private ExecutionContext ec
    private ExecutionContextFactoryImpl ecfi
    private MultiEntityHandler meh

    EndpointServiceHandler(){
        this.ec = Moqui.getExecutionContext()
        this.ecfi = (ExecutionContextFactoryImpl) Moqui.getExecutionContextFactory()
        this.meh = new MultiEntityHandler(this.ec)
    }

    public HashMap serveEndpoint()
    {
        def entityName = (String) ec.context.entityName
        def pageIndex = (Integer) ec.context.index
        def pageSize = (Integer) ec.context.size
        def orderBy = (ArrayList) ec.context.orderBy?:[]
        logger.info("entityName/index/size: ${entityName}/${pageIndex}/${pageSize}")

        EntityDefinition ed = this.meh.getDefinition(entityName, false)
        if (!ed) return [result: false, message: 'Entity definition not found, cannot continue with populating service output']

        def evs = ec.entity.find(entityName)
            .limit(pageSize)
            .offset(pageIndex * pageSize)

        // order by columns
        if (orderBy) evs.orderBy(orderBy)

        def res = []
        for (EntityValue ev in evs.list())
        {
            def evMap = [:]
            ev.keySet().each {
                evMap.put(it, ev.get(it))
            }

            // handle ID column


            res.push(evMap)
        }

        return [result: true, data: res]
    }

}
