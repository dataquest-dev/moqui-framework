package dtq.rockycube.messaging

import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ToolFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MessageRelayToolFactory implements ToolFactory<MessageRelayTool> {
    protected final static Logger logger = LoggerFactory.getLogger(MessageRelayToolFactory.class)
    protected ExecutionContextFactory ecf
    final static String TOOL_NAME = "MessageRelayTool"

    // the tool itself
    protected MessageRelayTool messaging

    /** Default empty constructor */
    MessageRelayToolFactory() { }

    @Override
    String getName() { return TOOL_NAME }

    @Override
    void init(ExecutionContextFactory ecf) {
        this.ecf = ecf

        // load parameters from setup
        String uri = System.properties.get("jms.server.host")

        // do not allow Tool to be started, should there be no connection info
        if (!uri)
        {
            logger.warn("Cannot initialize MessageRelayTool, no URI set")
            return
        }

        logger.info("Starting MessageRelayTool on '${uri}'")

        /*initializing client*/
        Map<String, String> httpHeaders = new HashMap<String, String>()

        messaging = new MessageRelayTool(ecf.executionContext, uri, httpHeaders)
        messaging.connectBlocking()

    }

    @Override
    void preFacadeInit(ExecutionContextFactory ecf) {}

    @Override
    MessageRelayTool getInstance(Object... parameters) {
        if (messaging == null) throw new IllegalStateException("MessageRelayTool not initialized")
        return messaging
    }

    @Override
    void destroy() {
        if (messaging != null) try {
            messaging.closeConnection(0, 'Closing connection on tool destruction')

            logger.info("MessageRelayTool closed")
        } catch (Throwable t) { logger.error("Error while MessageRelayTool client close procedure.", t) }
    }

    ExecutionContextFactory getEcf() { return ecf }
}
