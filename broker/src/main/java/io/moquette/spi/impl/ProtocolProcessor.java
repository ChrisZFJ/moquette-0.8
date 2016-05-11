/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.moquette.spi.impl;

import static io.moquette.parser.netty.Utils.VERSION_3_1;
import static io.moquette.parser.netty.Utils.VERSION_3_1_1;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.moquette.payload.protobuf.PayloadProtoBuf;
import io.moquette.proto.messages.AbstractMessage;
import io.moquette.proto.messages.AbstractMessage.QOSType;
import io.moquette.proto.messages.ConnAckMessage;
import io.moquette.proto.messages.ConnectMessage;
import io.moquette.proto.messages.PubAckMessage;
import io.moquette.proto.messages.PubCompMessage;	
import io.moquette.proto.messages.PubRecMessage;
import io.moquette.proto.messages.PubRelMessage;
import io.moquette.proto.messages.PublishMessage;
import io.moquette.proto.messages.SubAckMessage;
import io.moquette.proto.messages.SubscribeMessage;
import io.moquette.proto.messages.UnsubAckMessage;
import io.moquette.proto.messages.UnsubscribeMessage;
import io.moquette.server.ConnectionDescriptor;
import io.moquette.server.ServerChannel;
import io.moquette.server.netty.NettyChannel;
import io.moquette.spi.ClientSession;
import io.moquette.spi.IMatchingCondition;
import io.moquette.spi.IMessagesStore;
import io.moquette.spi.ISessionsStore;
import io.moquette.spi.impl.subscriptions.Subscription;
import io.moquette.spi.impl.subscriptions.SubscriptionsStore;
import io.moquette.spi.security.IAuthenticator;
import io.moquette.spi.security.IAuthorizator;

/**
 * Class responsible to handle the logic of MQTT protocol it's the director of
 * the protocol execution. 
 * 
 * Used by the front facing class SimpleMessaging.
 * 
 * @author andrea
 */

/**
 * 扩展了getFriendsStates()接口，用户实现批量获取好友的状态
 * 
 * 
 * @author cuidonghuan
 *
 */

public class ProtocolProcessor {

    static final class WillMessage {
        private final String topic;
        private final ByteBuffer payload;
        private final boolean retained;
        private final QOSType qos;

        public WillMessage(String topic, ByteBuffer payload, boolean retained, QOSType qos) {
            this.topic = topic;
            this.payload = payload;
            this.retained = retained;
            this.qos = qos;
        }

        public String getTopic() {
            return topic;
        }

        public ByteBuffer getPayload() {
            return payload;
        }

        public boolean isRetained() {
            return retained;
        }

        public QOSType getQos() {
            return qos;
        }
        
    }
    
    private static final Logger LOG = LoggerFactory.getLogger(ProtocolProcessor.class);
    
    protected ConcurrentMap<String, ConnectionDescriptor> m_clientIDs;
    private SubscriptionsStore subscriptions;
    private boolean allowAnonymous;
    private IAuthorizator m_authorizator;
    private IMessagesStore m_messagesStore;
    private ISessionsStore m_sessionsStore;
    private IAuthenticator m_authenticator;
    private BrokerInterceptor m_interceptor;

    //maps clientID to Will testament, if specified on CONNECT
    private ConcurrentMap<String, WillMessage> m_willStore = new ConcurrentHashMap<>();
    
    ProtocolProcessor() {}

    /**
     * @param subscriptions the subscription store where are stored all the existing
     *  clients subscriptions.
     * @param storageService the persistent store to use for save/load of messages
     *  for QoS1 and QoS2 handling.
     * @param sessionsStore the clients sessions store, used to persist subscriptions.
     * @param authenticator the authenticator used in connect messages.
     * @param allowAnonymous true connection to clients without credentials.
     * @param authorizator used to apply ACL policies to publishes and subscriptions.
     * @param interceptor to notify events to an intercept handler
     */
    void init(SubscriptionsStore subscriptions, IMessagesStore storageService,
              ISessionsStore sessionsStore,
              IAuthenticator authenticator,
              boolean allowAnonymous, IAuthorizator authorizator, BrokerInterceptor interceptor) {
        this.m_clientIDs = new ConcurrentHashMap<>();
        this.m_interceptor = interceptor;
        this.subscriptions = subscriptions;
        this.allowAnonymous = allowAnonymous;
        m_authorizator = authorizator;
        LOG.trace("subscription tree on init {}", subscriptions.dumpTree());
        m_authenticator = authenticator;
        m_messagesStore = storageService;
        m_sessionsStore = sessionsStore;
    }

    /**
     * 处理“连接”请求, 请求各参数正确, 且m_clientIDs中不包含该clientID, 需要在m_clientIDs中add该clientID.
     * @param session
     * @param msg
     */
    public void processConnect(ServerChannel session, ConnectMessage msg) {
        LOG.debug("CONNECT for client <{}>", msg.getClientID());
        
        // MQTT协议版本错误或者无法识别
        if (msg.getProtocolVersion() != VERSION_3_1 && msg.getProtocolVersion() != VERSION_3_1_1) {
            ConnAckMessage badProto = new ConnAckMessage();
            badProto.setReturnCode(ConnAckMessage.UNNACEPTABLE_PROTOCOL_VERSION);
            LOG.warn("processConnect sent bad proto ConnAck");
            session.write(badProto);
            session.close(false);
            return;
        }

        // CONNECT报文中ClientID为空
        if (msg.getClientID() == null || msg.getClientID().length() == 0) {
            ConnAckMessage okResp = new ConnAckMessage();
            okResp.setReturnCode(ConnAckMessage.IDENTIFIER_REJECTED);
            session.write(okResp);
            m_interceptor.notifyClientConnected(msg);
            return;
        }

        //handle user authentication，验证用户身份
        if (msg.isUserFlag()) {
            byte[] pwd = null;
            if (msg.isPasswordFlag()) {
                pwd = msg.getPassword();
            } else if (!this.allowAnonymous) {
                failedCredentials(session);
                return;
            }
            if (!m_authenticator.checkValid(msg.getUsername(), pwd)) {
                failedCredentials(session);
                return;
            }
            session.setAttribute(NettyChannel.ATTR_KEY_USERNAME, msg.getUsername());
        } else if (!this.allowAnonymous) {
            failedCredentials(session);
            return;
        }

        //if an old client with the same ID already exists close its session..类似于异地登录的处理
        if (m_clientIDs.containsKey(msg.getClientID())) {
            LOG.info("Found an existing connection with same client ID <{}>, forcing to close", msg.getClientID());
            //clean the subscriptions if the old used a cleanSession = true
            ServerChannel oldSession = m_clientIDs.get(msg.getClientID()).session;
            ClientSession oldClientSession = m_sessionsStore.sessionForClient(msg.getClientID());
            
            // 这里可以处理异地登录问题，在关闭旧的oldClientSession之前，先给旧的cliendId客户端发送一个PublishMessage，告诉他有人把他挤掉线了
            // 这个功能以后再扩展实现...............................................................
            
            oldClientSession.disconnect();
            oldSession.setAttribute(NettyChannel.ATTR_KEY_SESSION_STOLEN, true);
            oldSession.close(false);
            LOG.debug("Existing connection with same client ID <{}>, forced to close", msg.getClientID());
        }

        // 正常情况下的处理(clientID正确，没有重复clientID在线)
        ConnectionDescriptor connDescr = new ConnectionDescriptor(msg.getClientID(), session, msg.isCleanSession());
        m_clientIDs.put(msg.getClientID(), connDescr);

        int keepAlive = msg.getKeepAlive();
        LOG.debug("Connect with keepAlive {} s",  keepAlive);
        session.setAttribute(NettyChannel.ATTR_KEY_KEEPALIVE, keepAlive);
        session.setAttribute(NettyChannel.ATTR_KEY_CLEANSESSION, msg.isCleanSession());
        //used to track the client in the subscription and publishing phases.
        session.setAttribute(NettyChannel.ATTR_KEY_CLIENTID, msg.getClientID());
        LOG.debug("Connect create session <{}>", session);

        session.setIdleTime(Math.round(keepAlive * 1.5f));

        //Handle will flag, 处理“遗嘱”问题
        if (msg.isWillFlag()) {
            AbstractMessage.QOSType willQos = AbstractMessage.QOSType.valueOf(msg.getWillQos());
            byte[] willPayload = msg.getWillMessage();
            ByteBuffer bb = (ByteBuffer) ByteBuffer.allocate(willPayload.length).put(willPayload).flip();
            //save the will testament in the clientID store
            WillMessage will = new WillMessage(msg.getWillTopic(), bb, msg.isWillRetain(),willQos );
            m_willStore.put(msg.getClientID(), will);
        }

        // 连接成功，像客户端发送连接返回码
        ConnAckMessage okResp = new ConnAckMessage();
        okResp.setReturnCode(ConnAckMessage.CONNECTION_ACCEPTED);

        ClientSession clientSession = m_sessionsStore.sessionForClient(msg.getClientID());
        boolean isSessionAlreadyStored = clientSession != null;
        if (!msg.isCleanSession() && isSessionAlreadyStored) {
            okResp.setSessionPresent(true);
        }
        session.write(okResp);
        // 正式连接成功，确认也已经发送
        m_interceptor.notifyClientConnected(msg);

        // 该clientID第一次连接，创建新的ClientSession
        if (!isSessionAlreadyStored) {
            LOG.info("Create persistent session for clientID <{}>", msg.getClientID());
            clientSession = m_sessionsStore.createNewSession(msg.getClientID(), msg.isCleanSession());
        }
        clientSession.activate();
        
        // clientSession为true, 清空ClientSession
        if (msg.isCleanSession()) {
            clientSession.cleanSession();
        }
        LOG.info("Connected client ID <{}> with clean session {}", msg.getClientID(), msg.isCleanSession());
        if (!msg.isCleanSession()) {
            //force the republish of stored QoS1 and QoS2
            republishStoredInSession(clientSession);
        }
        LOG.info("CONNECT processed");
//        LOG.info("CONNECT clients descriptors {}", m_clientIDs);
        
        // cuidonghuan添加，扩展“用户上线，推送该状态给所有好友”功能;
        // 通知当前用户的所有好友其在线状态发生改变(上线)
        // msg.getClientId()不是服务器使用的clientId时，推送状态改变的通知
        if (!containsCertainStrings(msg.getClientID())) {
        	notifyStatesChanged(msg.getClientID(), true);
		}
    }

    private void failedCredentials(ServerChannel session) {
        ConnAckMessage okResp = new ConnAckMessage();
        okResp.setReturnCode(ConnAckMessage.BAD_USERNAME_OR_PASSWORD);
        session.write(okResp);
        session.close(false);
    }

    /**
     * Republish QoS1 and QoS2 messages stored into the session for the clientID.
     * */
    private void republishStoredInSession(ClientSession clientSession) {
        LOG.trace("republishStoredInSession for client <{}>", clientSession);
        List<IMessagesStore.StoredMessage> publishedEvents = clientSession.storedMessages();
        if (publishedEvents.isEmpty()) {
            LOG.info("No stored messages for client <{}>", clientSession.clientID);
            return;
        }

        LOG.info("republishing stored messages to client <{}>", clientSession.clientID);
        for (IMessagesStore.StoredMessage pubEvt : publishedEvents) {
            //TODO put in flight zone
            directSend(clientSession, pubEvt.getTopic(), pubEvt.getQos(),
                    pubEvt.getMessage(), false, pubEvt.getMessageID());
            clientSession.removeEnqueued(pubEvt.getGuid());
        }
    }
    
    public void processPubAck(ServerChannel session, PubAckMessage msg) {
        String clientID = (String) session.getAttribute(NettyChannel.ATTR_KEY_CLIENTID);
        int messageID = msg.getMessageID();
        //Remove the message from message store
        ClientSession targetSession = m_sessionsStore.sessionForClient(clientID);
        verifyToActivate(clientID, targetSession);
        targetSession.inFlightAcknowledged(messageID);
    }

    private void verifyToActivate(String clientID, ClientSession targetSession) {
        if (m_clientIDs.containsKey(clientID)) {
            targetSession.activate();
        }
    }

    private static IMessagesStore.StoredMessage asStoredMessage(PublishMessage msg) {
        IMessagesStore.StoredMessage stored = new IMessagesStore.StoredMessage(msg.getPayload().array(), msg.getQos(), msg.getTopicName());
        stored.setRetained(msg.isRetainFlag());
        stored.setMessageID(msg.getMessageID());
        return stored;
    }

    private static IMessagesStore.StoredMessage asStoredMessage(WillMessage will) {
        IMessagesStore.StoredMessage pub = new IMessagesStore.StoredMessage(will.getPayload().array(), will.getQos(), will.getTopic());
        pub.setRetained(will.isRetained());
        return pub;
    }
    
    public void processPublish(ServerChannel session, PublishMessage msg) {
        LOG.trace("PUB --PUBLISH--> SRV executePublish invoked with {}", msg);
        String clientID = (String) session.getAttribute(NettyChannel.ATTR_KEY_CLIENTID);
        final String topic = msg.getTopicName();
        //check if the topic can be wrote
        String user = (String) session.getAttribute(NettyChannel.ATTR_KEY_USERNAME);
        if (!m_authorizator.canWrite(topic, user, clientID)) {
            LOG.debug("topic {} doesn't have write credentials", topic);
            return;
        }
        final AbstractMessage.QOSType qos = msg.getQos();
        final Integer messageID = msg.getMessageID();
        LOG.info("PUBLISH from clientID <{}> on topic <{}> with QoS {}", clientID, topic, qos);

        String guid = null;
        
        // 将PublishMessage持久化保存
        IMessagesStore.StoredMessage toStoreMsg = asStoredMessage(msg);
        toStoreMsg.setClientID(clientID);
        
        if (qos == AbstractMessage.QOSType.MOST_ONE) { //QoS0
            route2Subscribers(toStoreMsg);
        } else if (qos == AbstractMessage.QOSType.LEAST_ONE) { //QoS1
            route2Subscribers(toStoreMsg);
            sendPubAck(clientID, messageID);
            LOG.debug("replying with PubAck to MSG ID {}", messageID);
        } else if (qos == AbstractMessage.QOSType.EXACTLY_ONCE) { //QoS2
            guid = m_messagesStore.storePublishForFuture(toStoreMsg);
            sendPubRec(clientID, messageID);
            //Next the client will send us a pub rel
            //NB publish to subscribers for QoS 2 happen upon PUBREL from publisher
        }

        if (msg.isRetainFlag()) {
            if (qos == AbstractMessage.QOSType.MOST_ONE) {
                //QoS == 0 && retain => clean old retained
                m_messagesStore.cleanRetained(topic);
            } else {
                if (!msg.getPayload().hasRemaining()) {
                    m_messagesStore.cleanRetained(topic);
                } else {
                    if (guid == null) {
                        //before wasn't stored
                        guid = m_messagesStore.storePublishForFuture(toStoreMsg);
                    }
                    m_messagesStore.storeRetained(topic, guid);
                }
            }
        }
        m_interceptor.notifyTopicPublished(msg, clientID);
    }

    /**
     * Intended usage is only for embedded versions of the broker, where the hosting application want to use the
     * broker to send a publish message.
     * Inspired by {@link #processPublish} but with some changes to avoid security check, and the handshake phases
     * for Qos1 and Qos2.
     * It also doesn't notifyTopicPublished because using internally the owner should already know where
     * it's publishing.
     * */
//    public void internalPublish(PublishMessage msg) {
//        final AbstractMessage.QOSType qos = msg.getQos();
//        final String topic = msg.getTopicName();
//        LOG.info("embedded PUBLISH on topic <{}> with QoS {}", topic, qos);
//
//        String guid = null;
//        IMessagesStore.StoredMessage toStoreMsg = asStoredMessage(msg);
//        toStoreMsg.setClientID("BROKER_SELF");
//        toStoreMsg.setMessageID(1);
//        if (qos == AbstractMessage.QOSType.MOST_ONE || qos == AbstractMessage.QOSType.LEAST_ONE) { //QoS0, QoS1
//            route2Subscribers(toStoreMsg);
//        } else if (qos == AbstractMessage.QOSType.EXACTLY_ONCE) { //QoS2
//            guid = m_messagesStore.storePublishForFuture(toStoreMsg);
//
//            route2Subscribers(toStoreMsg);
//
//            if (toStoreMsg.isRetained()) {
//                if (!toStoreMsg.getMessage().hasRemaining()) {
//                    m_messagesStore.cleanRetained(topic);
//                } else {
//                    m_messagesStore.storeRetained(topic, guid);
//                }
//            }
//            return;
//        }
//
//        if (msg.isRetainFlag()) {
//            if (qos == AbstractMessage.QOSType.MOST_ONE) {
//                //QoS == 0 && retain => clean old retained
//                m_messagesStore.cleanRetained(topic);
//            } else {
//                if (!msg.getPayload().hasRemaining()) {
//                    m_messagesStore.cleanRetained(topic);
//                } else {
//                    if (guid == null) {
//                        //before wasn't stored
//                        guid = m_messagesStore.storePublishForFuture(toStoreMsg);
//                    }
//                    m_messagesStore.storeRetained(topic, guid);
//                }
//            }
//        }
//    }

    public void internalPublish(PublishMessage msg) {
        final AbstractMessage.QOSType qos = msg.getQos();
        final String topic = msg.getTopicName();
        LOG.info("embedded PUBLISH on topic <{}> with QoS {}", topic, qos);

        String guid = null;
        IMessagesStore.StoredMessage toStoreMsg = asStoredMessage(msg);
        toStoreMsg.setClientID("BROKER_SELF");
        toStoreMsg.setMessageID(1);
        if (qos == AbstractMessage.QOSType.EXACTLY_ONCE) { //QoS2
            guid = m_messagesStore.storePublishForFuture(toStoreMsg);
        }
        route2Subscribers(toStoreMsg);

        if (!msg.isRetainFlag()) {
            return;
        }
        if (qos == AbstractMessage.QOSType.MOST_ONE || !msg.getPayload().hasRemaining()) {
            //QoS == 0 && retain => clean old retained
            m_messagesStore.cleanRetained(topic);
            return;
        }
        if (guid == null) {
            //before wasn't stored
            guid = m_messagesStore.storePublishForFuture(toStoreMsg);
        }
        m_messagesStore.storeRetained(topic, guid);
    }
        
    /**
     * Specialized version to publish will testament message.
     */
    private void forwardPublishWill(WillMessage will, String clientID) {
        //it has just to publish the message downstream to the subscribers
        //NB it's a will publish, it needs a PacketIdentifier for this conn, default to 1
        Integer messageId = null;
        if (will.getQos() != AbstractMessage.QOSType.MOST_ONE) {
            messageId = m_sessionsStore.nextPacketID(clientID);
        }

        IMessagesStore.StoredMessage tobeStored = asStoredMessage(will);
        tobeStored.setClientID(clientID);
        tobeStored.setMessageID(messageId);
        route2Subscribers(tobeStored);
    }


    /**
     * Flood the subscribers with the message to notify. MessageID is optional and should only used for QoS 1 and 2
     * */
    void route2Subscribers(IMessagesStore.StoredMessage pubMsg) {
        final String topic = pubMsg.getTopic();
        final AbstractMessage.QOSType publishingQos = pubMsg.getQos();
        final ByteBuffer origMessage = pubMsg.getMessage();
        LOG.debug("route2Subscribers republishing to existing subscribers that matches the topic {}", topic);
        if (LOG.isTraceEnabled()) {
            LOG.trace("content <{}>", DebugUtils.payload2Str(origMessage));
            LOG.trace("subscription tree {}", subscriptions.dumpTree());
        }
        //if QoS 1 or 2 store the message
        String guid = null;
        if (publishingQos == QOSType.EXACTLY_ONCE || publishingQos == QOSType.LEAST_ONE) {
            guid = m_messagesStore.storePublishForFuture(pubMsg);
        }

        for (final Subscription sub : subscriptions.matches(topic)) {
            AbstractMessage.QOSType qos = publishingQos;
            if (qos.byteValue() > sub.getRequestedQos().byteValue()) {
                qos = sub.getRequestedQos();
            }
            ClientSession targetSession = m_sessionsStore.sessionForClient(sub.getClientId());
            verifyToActivate(sub.getClientId(), targetSession);

            LOG.debug("Broker republishing to client <{}> topic <{}> qos <{}>, active {}",
                    sub.getClientId(), sub.getTopicFilter(), qos, targetSession.isActive());
            ByteBuffer message = origMessage.duplicate();
            if (qos == AbstractMessage.QOSType.MOST_ONE && targetSession.isActive()) {
                //QoS 0
                directSend(targetSession, topic, qos, message, false, null);
            } else {
                //QoS 1 or 2
                //if the target subscription is not clean session and is not connected => store it
                if (!targetSession.isCleanSession() && !targetSession.isActive()) {
                    //store the message in targetSession queue to deliver
                    targetSession.enqueueToDeliver(guid);
                } else  {
                    //publish
                    if (targetSession.isActive()) {
                        int messageId = targetSession.nextPacketId();
                        targetSession.inFlightAckWaiting(guid, messageId);
                        directSend(targetSession, topic, qos, message, false, messageId);
                    }
                }
            }
        }
    }

    protected void directSend(ClientSession clientsession, String topic, AbstractMessage.QOSType qos, ByteBuffer message, boolean retained, Integer messageID) {
        String clientId = clientsession.clientID;
        LOG.debug("directSend invoked clientId <{}> on topic <{}> QoS {} retained {} messageID {}", clientId, topic, qos, retained, messageID);
        PublishMessage pubMessage = new PublishMessage();
        pubMessage.setRetainFlag(retained);
        pubMessage.setTopicName(topic);
        pubMessage.setQos(qos);
        pubMessage.setPayload(message);
        
        LOG.info("send publish message to <{}> on topic <{}>", clientId, topic);
        if (LOG.isDebugEnabled()) {
            LOG.debug("content <{}>", DebugUtils.payload2Str(message));
        }
        //set the PacketIdentifier only for QoS > 0
        if (pubMessage.getQos() != AbstractMessage.QOSType.MOST_ONE) {
            pubMessage.setMessageID(messageID);
        } else {
            if (messageID != null) {
                throw new RuntimeException("Internal bad error, trying to forwardPublish a QoS 0 message with PacketIdentifier: " + messageID);
            }
        }

        if (m_clientIDs == null) {
            throw new RuntimeException("Internal bad error, found m_clientIDs to null while it should be initialized, somewhere it's overwritten!!");
        }
        LOG.debug("clientIDs are {}", m_clientIDs);
        if (m_clientIDs.get(clientId) == null) {
            //TODO while we were publishing to the target client, that client disconnected,
            // could happen is not an error HANDLE IT
            throw new RuntimeException(String.format("Can't find a ConnectionDescriptor for client <%s> in cache <%s>", clientId, m_clientIDs));
        }
        ServerChannel session = m_clientIDs.get(clientId).session;
        LOG.debug("Session for clientId {} is {}", clientId, session);

        String user = (String) session.getAttribute(NettyChannel.ATTR_KEY_USERNAME);
        if (!m_authorizator.canRead(topic, user, clientId)) {
            LOG.debug("topic {} doesn't have read credentials", topic);
            return;
        }
        session.write(pubMessage);
    }
    
    private void sendPubRec(String clientID, int messageID) {
        LOG.trace("PUB <--PUBREC-- SRV sendPubRec invoked for clientID {} with messageID {}", clientID, messageID);
        PubRecMessage pubRecMessage = new PubRecMessage();
        pubRecMessage.setMessageID(messageID);
        m_clientIDs.get(clientID).session.write(pubRecMessage);
    }
    
    private void sendPubAck(String clientId, int messageID) {
        LOG.trace("sendPubAck invoked");
        PubAckMessage pubAckMessage = new PubAckMessage();
        pubAckMessage.setMessageID(messageID);

        try {
            if (m_clientIDs == null) {
                throw new RuntimeException("Internal bad error, found m_clientIDs to null while it should be initialized, somewhere it's overwritten!!");
            }
            LOG.debug("clientIDs are {}", m_clientIDs);
            if (m_clientIDs.get(clientId) == null) {
                throw new RuntimeException(String.format("Can't find a ConnectionDescriptor for client %s in cache %s", clientId, m_clientIDs));
            }
            m_clientIDs.get(clientId).session.write(pubAckMessage);
        } catch(Throwable t) {
            LOG.error(null, t);
        }
    }
    
    /**
     * Second phase of a publish QoS2 protocol, sent by publisher to the broker. Search the stored message and publish
     * to all interested subscribers.
     * */
    public void processPubRel(ServerChannel session, PubRelMessage msg) {
        String clientID = (String) session.getAttribute(NettyChannel.ATTR_KEY_CLIENTID);
        int messageID = msg.getMessageID();
        LOG.debug("PUB --PUBREL--> SRV processPubRel invoked for clientID {} ad messageID {}", clientID, messageID);
        ClientSession targetSession = m_sessionsStore.sessionForClient(clientID);
        verifyToActivate(clientID, targetSession);
        IMessagesStore.StoredMessage evt = targetSession.storedMessage(messageID);
        route2Subscribers(evt);

        if (evt.isRetained()) {
            final String topic = evt.getTopic();
            if (!evt.getMessage().hasRemaining()) {
                m_messagesStore.cleanRetained(topic);
            } else {
                m_messagesStore.storeRetained(topic, evt.getGuid());
            }
        }

        sendPubComp(clientID, messageID);
    }
    
    private void sendPubComp(String clientID, int messageID) {
        LOG.debug("PUB <--PUBCOMP-- SRV sendPubComp invoked for clientID {} ad messageID {}", clientID, messageID);
        PubCompMessage pubCompMessage = new PubCompMessage();
        pubCompMessage.setMessageID(messageID);

        m_clientIDs.get(clientID).session.write(pubCompMessage);
    }
    
    public void processPubRec(ServerChannel session, PubRecMessage msg) {
        String clientID = (String) session.getAttribute(NettyChannel.ATTR_KEY_CLIENTID);
        int messageID = msg.getMessageID();
        ClientSession targetSession = m_sessionsStore.sessionForClient(clientID);
        verifyToActivate(clientID, targetSession);
        //remove from the inflight and move to the QoS2 second phase queue
        targetSession.inFlightAcknowledged(messageID);
        targetSession.secondPhaseAckWaiting(messageID);
        //once received a PUBREC reply with a PUBREL(messageID)
        LOG.debug("\t\tSRV <--PUBREC-- SUB processPubRec invoked for clientID {} ad messageID {}", clientID, messageID);
        PubRelMessage pubRelMessage = new PubRelMessage();
        pubRelMessage.setMessageID(messageID);
        pubRelMessage.setQos(AbstractMessage.QOSType.LEAST_ONE);

        session.write(pubRelMessage);
    }

    public void processPubComp(ServerChannel session, PubCompMessage msg) {
        String clientID = (String) session.getAttribute(NettyChannel.ATTR_KEY_CLIENTID);
        int messageID = msg.getMessageID();
        LOG.debug("\t\tSRV <--PUBCOMP-- SUB processPubComp invoked for clientID {} ad messageID {}", clientID, messageID);
        //once received the PUBCOMP then remove the message from the temp memory
        ClientSession targetSession = m_sessionsStore.sessionForClient(clientID);
        verifyToActivate(clientID, targetSession);
        targetSession.secondPhaseAcknowledged(messageID);
    }
    
    /**
     * 处理“断开连接”操作, 需要从m_clientIDs中remove掉该clientID
     * @param session
     * @throws InterruptedException
     */
    public void processDisconnect(ServerChannel session) throws InterruptedException {
        String clientID = (String) session.getAttribute(NettyChannel.ATTR_KEY_CLIENTID);
        boolean cleanSession = (Boolean) session.getAttribute(NettyChannel.ATTR_KEY_CLEANSESSION);
        LOG.info("DISCONNECT client <{}> with clean session {}", clientID, cleanSession);
        ClientSession clientSession = m_sessionsStore.sessionForClient(clientID);
        clientSession.disconnect();

        // 成功断开连接之后，将clientID从m_clientIDs中remove掉.
        m_clientIDs.remove(clientID);
        session.close(true);

        //cleanup the will store
        m_willStore.remove(clientID);
        
        m_interceptor.notifyClientDisconnected(clientID);
        LOG.info("DISCONNECT client <{}> finished", clientID, cleanSession);
        
        // cuidonghuan添加，扩展“用户上线，推送该状态给所有好友”功能;
        // 通知当前用户的所有好友其在线状态发生改变（离线）
        // clientID不是服务器使用的clientId时，推送状态改变的通知
        if (!containsCertainStrings(clientID)) {
        	notifyStatesChanged(clientID, true);
		}
        
    }

    public void processConnectionLost(String clientID, boolean sessionStolen, NettyChannel channel) {
        ConnectionDescriptor oldConnDescr = new ConnectionDescriptor(clientID, channel, true);
        m_clientIDs.remove(clientID, oldConnDescr);
        //If already removed a disconnect message was already processed for this clientID
        if (sessionStolen) {
            //de-activate the subscriptions for this ClientID
            ClientSession clientSession = m_sessionsStore.sessionForClient(clientID);
            clientSession.deactivate();
            LOG.info("Lost connection with client <{}>", clientID);
        }
        //publish the Will message (if any) for the clientID
        if (!sessionStolen && m_willStore.containsKey(clientID)) {
            WillMessage will = m_willStore.get(clientID);
            forwardPublishWill(will, clientID);
            m_willStore.remove(clientID);
        }
        
        // cuidonghuan添加，扩展“用户上线，推送该状态给所有好友”功能;
        // 通知当前用户的所有好友其在线状态发生改变(掉线)
        // clientID不是服务器使用的clientId时，推送状态改变的通知
        if (!containsCertainStrings(clientID)) {
        	notifyStatesChanged(clientID, false);
		}
    }
    
    /**
     * Remove the clientID from topic subscription, if not previously subscribed,
     * doesn't reply any error
     */
    public void processUnsubscribe(ServerChannel session, UnsubscribeMessage msg) {
        List<String> topics = msg.topicFilters();
        int messageID = msg.getMessageID();
        String clientID = (String) session.getAttribute(NettyChannel.ATTR_KEY_CLIENTID);
        LOG.debug("UNSUBSCRIBE subscription on topics {} for clientID <{}>", topics, clientID);

        ClientSession clientSession = m_sessionsStore.sessionForClient(clientID);
        verifyToActivate(clientID, clientSession);
        for (String topic : topics) {
            boolean validTopic = SubscriptionsStore.validate(topic);
            if (!validTopic) {
                //close the connection, not valid topicFilter is a protocol violation
                session.close(true);
                LOG.warn("UNSUBSCRIBE found an invalid topic filter <{}> for clientID <{}>", topic, clientID);
                return;
            }

            subscriptions.removeSubscription(topic, clientID);
            clientSession.unsubscribeFrom(topic);
            m_interceptor.notifyTopicUnsubscribed(topic, clientID);
        }

        //ack the client
        UnsubAckMessage ackMessage = new UnsubAckMessage();
        ackMessage.setMessageID(messageID);

        LOG.info("replying with UnsubAck to MSG ID {}", messageID);
        session.write(ackMessage);
    }

    public void processSubscribe(ServerChannel session, SubscribeMessage msg) {
        String clientID = (String) session.getAttribute(NettyChannel.ATTR_KEY_CLIENTID);
        LOG.debug("SUBSCRIBE client <{}> packetID {}", clientID, msg.getMessageID());

        ClientSession clientSession = m_sessionsStore.sessionForClient(clientID);
        verifyToActivate(clientID, clientSession);
        //ack the client
        SubAckMessage ackMessage = new SubAckMessage();
        ackMessage.setMessageID(msg.getMessageID());

        List<Subscription> newSubscriptions = new ArrayList<>();
        for (SubscribeMessage.Couple req : msg.subscriptions()) {
            AbstractMessage.QOSType qos = AbstractMessage.QOSType.valueOf(req.getQos());
            Subscription newSubscription = new Subscription(clientID, req.getTopicFilter(), qos);
            //boolean valid = subscribeSingleTopic(newSubscription, req.getTopicFilter());
            boolean valid = clientSession.subscribe(req.getTopicFilter(), newSubscription);
            ackMessage.addType(valid ? qos : AbstractMessage.QOSType.FAILURE);
            if (valid) {
                newSubscriptions.add(newSubscription);
            }
        }

        //save session, persist subscriptions from session
        LOG.debug("SUBACK for packetID {}", msg.getMessageID());
        if (LOG.isTraceEnabled()) {
            LOG.trace("subscription tree {}", subscriptions.dumpTree());
        }
        session.write(ackMessage);

        //fire the publish
        for(Subscription subscription : newSubscriptions) {
            subscribeSingleTopic(subscription);
        }
    }
    
    private boolean subscribeSingleTopic(final Subscription newSubscription) {
        subscriptions.add(newSubscription.asClientTopicCouple());

        //scans retained messages to be published to the new subscription
        //TODO this is ugly, it does a linear scan on potential big dataset
        Collection<IMessagesStore.StoredMessage> messages = m_messagesStore.searchMatching(new IMatchingCondition() {
            public boolean match(String key) {
                return SubscriptionsStore.matchTopics(key, newSubscription.getTopicFilter());
            }
        });

        ClientSession targetSession = m_sessionsStore.sessionForClient(newSubscription.getClientId());
        verifyToActivate(newSubscription.getClientId(), targetSession);
        for (IMessagesStore.StoredMessage storedMsg : messages) {
            //fire the as retained the message
            LOG.debug("send publish message for topic {}", newSubscription.getTopicFilter());
            //forwardPublishQoS0(newSubscription.getClientId(), storedMsg.getTopic(), storedMsg.getQos(), storedMsg.getPayload(), true);
            Integer packetID = storedMsg.getQos() == QOSType.MOST_ONE ? null :
                    targetSession.nextPacketId();
            directSend(targetSession, storedMsg.getTopic(), storedMsg.getQos(), storedMsg.getPayload(), true, packetID);
        }

        //notify the Observables
        m_interceptor.notifyTopicSubscribed(newSubscription);
        return true;
    }
    
    /**
     * Moquette扩展功能: 获取给定的好友列表中各好友的在线状态
     * cuidonghuan添加，扩展“获取给定的好友列表中各好友的在线状态”功能;
     * @param friendsList 需要查询状态的好友列表
     * @return 好友列表中各好友的在线状态集合
     * @author cuidonghuan
     */
    public List<Boolean> getFriendsStates(List<String> friendsList) {
    	
    	List<Boolean> friendStates = new ArrayList<Boolean>();
    	boolean isOnline;
    	for(String friendClientID : friendsList) {
    		isOnline = m_clientIDs.containsKey(friendClientID);
    		friendStates.add(new Boolean(isOnline));
    		System.out.println(getCurrentTime(this) + "好友 [" + friendClientID + "] 在线状态: " + isOnline);
    	}
    	
    	return friendStates;
    }
    
    public static String getCurrentTime(Object object) {  
		
		String time = null;
		
		// 获取日期字符串
        String returnStr = null;  
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
        Date date = new Date();  
        returnStr = f.format(date);  
        
        // 获得完整包名
        String compClassName = "<" + object.getClass().getName() + "> : ";
        
        time = "[" + returnStr + "] " + compClassName;
        
        return time;  
    }
    
    /**
     * 通知当前用户的所有好友其状态发生了改变
     * 该功能采用Qos=0的Publish报文，主要是因为：
     * 一是messageID不好处理（一般是客户端发送过来的Qos=1 or 2 的Publish报文中包含messageID，而这里是模拟的Publish报文，如果自定义messageID可能会与客户端发送来的messageID冲突）
     * 二是只有客户端网络很差的情况下，才会出现收不到Qos=0的消息，而这种情况下，可以通过下拉刷新好友列表而采用上面的getFriendsStates()一次性获取所有好友的状态，或者放弃获取好友状态，
     * 就算是直接发送消息也是可以的，因为本身系统含有离线消息推送的功能；
     * cuidonghuan添加;
     * 
     * 本身上可以精细的区分出离线（主动断开连接）和掉线（被动断开连接）的状态改变，这里统一作为离线来处理了。
     * 
     * @param clientId 当前建立连接的用户的clientId
     * @param online 在线与否
     */
    private void notifyStatesChanged(String clientId, boolean online) {
    	
    	String statesTopicName = "states/" + clientId;
    	List<Subscription> matchedSubscriptions = subscriptions.matches(statesTopicName);
    	if (matchedSubscriptions == null || matchedSubscriptions.size() ==0) {
    		// 当前用户还没有任何好友
			return;
		}
    	
    	// 构造payload对象
    	PayloadProtoBuf.Payload.StatesManager states = PayloadProtoBuf.Payload.StatesManager
    			.newBuilder()
    			.setOnline(online)
    			.setUsername(clientId)
    			.build();
    	// MessageType:0x005,表示 IM_MESSAGE_TYPE_STATES 跟状态改变有关的消息类型
    	PayloadProtoBuf.Payload payloadProtoBuf = PayloadProtoBuf.Payload.newBuilder()
    			.setStatesManager(states)
    			.setMessageType(0x005)
    			.build();
    	ByteBuffer message = ByteBuffer.wrap(payloadProtoBuf.toByteArray());
    	message.rewind();
    	
    	PublishMessage pubMessage = new PublishMessage();
        pubMessage.setRetainFlag(false);
        pubMessage.setTopicName(statesTopicName);
        pubMessage.setQos(AbstractMessage.QOSType.MOST_ONE);
        pubMessage.setPayload(message);

        if (m_clientIDs == null) {
            throw new RuntimeException("Internal bad error, found m_clientIDs to null while it should be initialized, somewhere it's overwritten!!");
        }
        LOG.debug("clientIDs are {}", m_clientIDs);
        
        // 找到订阅了statesTopicName主题的所有在线订阅者，将状态改变（上线）消息pubMessage推送下去
        for (final Subscription sub : matchedSubscriptions) {
            
            // 该sub.getClientId()订阅者当前是离线状态，不推送pubMessage
            if (m_clientIDs.get(sub.getClientId()) == null) {
                //TODO while we were publishing to the target client, that client disconnected,
                // could happen is not an error HANDLE IT
//                throw new RuntimeException(String.format("Can't find a ConnectionDescriptor for client <%s> in cache <%s>", clientId, m_clientIDs));
            	continue;
            } else {
            	// 该sub.getClientId()订阅者当前是在线状态，推送pubMessage
            	ServerChannel session = m_clientIDs.get(sub.getClientId()).session;
                LOG.debug("[extends] send states changed message, Session for clientId {} is {}", sub.getClientId(), session);
                session.write(pubMessage);
                
                LOG.info("[extends] send states changed message to <{}> on topic <{}>, finished.发送状态改变信息给<{}>所有的好友", sub.getClientId(), statesTopicName, clientId);
            }
        }
        
        LOG.info("[extends] send states changed message to <{}>'s friends on topic <{}>, finished.", clientId, statesTopicName);
    }
    
    /**
     * 字符串str是否以"_server"结尾
     * @param str 需要判断的字符串
     * @return 判断结果
     */
    private boolean containsCertainStrings(String str) {
    	
    	if (str.length() < 7) {
			return false;
		}
    	
    	String subStr = str.substring(str.length() - 7, str.length() - 1);
    	if (!"_server".equals(subStr)) {
			return false;
		}
    	
    	// 该clientId属于服务器使用的clientId，当该clientIdConnect,Disconnect,ConnectLost时，不需要推送状态改变的通知；
    	return true;
    }
}