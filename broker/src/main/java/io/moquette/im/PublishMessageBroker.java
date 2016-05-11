package io.moquette.im;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.moquette.proto.messages.PublishMessage;

/**
 * Publish报文消息代理，负责向PublishMessageParser转发客户端发布的Publish报文.
 * 
 * ***************************************************************************************************************************
 * Publish报文消息转发实现原理：
 * 1.在tomcat启动时，实例化Server对象，将PublishMessageParser Class对象传递给Moquette.
 * 2.Moquette的Server类中，获得PublishMessageBroker的单例，将PublishMessageParser Class对象赋值给
 *   PublishMessageBroker单例中的publishMessageParser对象.
 * 3.Moquette中的ProtocolProcessor MQTT逻辑处理器，在processPublish()方法中调用PublishMessageBroker.getInstance().execute(msg), 
 *   msg对象即客户端发送的Publish报文对象, Web服务器端即获得了Publish报文对象, 可执行各种IM逻辑.
 * ***************************************************************************************************************************
 * 
 * ClassName: PublishMessageBroker 
 * @Description: 
 * @author cuidonghuan
 * @date 2016年5月11日 下午3:52:55
 */

public class PublishMessageBroker {
	
	private final static PublishMessageBroker broker = new PublishMessageBroker();
	
	private PublishMessageBroker() {}
	
	public static PublishMessageBroker getInstance() {
		return broker;
	}
	
	/** Publish报文解析器Class对象 **/
	private Class<?> publishMessageParser;

	public void setPublishMessageParser(Class<?> publishMessageParser) {
		this.publishMessageParser = publishMessageParser;
	}
	
	public void execute(PublishMessage publishMessage) {
		
		// 调用PublishMessageParser.parse(PublishMessage publishMessage)方法
		try {
			Method parse = publishMessageParser.getMethod("parse", PublishMessage.class);
			parse.invoke(publishMessageParser.newInstance(), publishMessage);
		} catch (NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			System.out.println("PublishMessageParser may not exist parse method!");
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
			System.out.println("publishMessageParser可能未初始化！");
		}
	}
}
