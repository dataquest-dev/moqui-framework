package dtq.rockycube.messaging

import com.google.gson.Gson
import org.moqui.context.ExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.java_websocket.client.WebSocketClient
import org.java_websocket.WebSocket
import org.java_websocket.WebSocketImpl
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake


/*
    Simple tool to relay messages to JMS, just a one way communication
 */

class MessageRelayTool extends WebSocketClient {
    protected final static Logger logger = LoggerFactory.getLogger(MessageRelayTool.class)
    protected ExecutionContext ec
    protected Gson gson

    public MessageRelayTool(URI serverUri, Draft draft) {
        super(serverUri, draft);
    }

    public MessageRelayTool(URI serverURI) {
        super(serverURI);
    }

    public MessageRelayTool(URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
    }

    // constructor - URL + PORT
    MessageRelayTool(ExecutionContext ec, String uri, HashMap<String, String> httpHeaders) {
        // default constructor
        super(new URI(uri), httpHeaders)

        this.ec = ec
        this.gson = new Gson()
    }


    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("opened connection: ${handshakedata}");
        // if you plan to refuse connection based on ip or httpfields overload: onWebsocketHandshakeReceivedAsClient
    }

    @Override
    public void onMessage(String message) {
        logger.info("received: " + message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // The close codes are documented in class org.java_websocket.framing.CloseFrame
        logger.info(
                "Connection closed by " + (remote ? "remote peer" : "us") + " Code: " + code + " Reason: "
                        + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
        logger.error(ex.message)
        // if the error is fatal then onClose will be called additionally
    }
}
