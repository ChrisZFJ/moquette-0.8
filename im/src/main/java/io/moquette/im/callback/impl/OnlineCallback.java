package io.moquette.im.callback.impl;

import io.moquette.im.processor.StateChangeProcessor;
import io.moquette.im.proxy.ProtocolProcessorProxy;
import io.moquette.proto.messages.ConnectMessage;
import io.moquette.server.ServerChannel;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

public class OnlineCallback implements MethodInterceptor {

	@Override
	public Object intercept(Object obj, Method method, Object[] args,
			MethodProxy proxy) throws Throwable {
		
		Object result = null;
		// 上线通知等逻辑操作...
		result = proxy.invokeSuper(obj, args);
		
		String clientId = ((ConnectMessage) args[1]).getClientID();
		// 回调上线处理方法
		// ProtocolProcessor-> public void processConnect(ServerChannel session, ConnectMessage msg)
		StateChangeProcessor.getInstance(null).onOnline(clientId);
		
		// 推送上线通知
		if(!ProtocolProcessorProxy.getInstance().containsCertainStrings(clientId)) {
			// 调用notifyStatesChanged()方法
			ProtocolProcessorProxy.getInstance().notifyStatesChanged(((ServerChannel)args[0]), true);
			System.out.println(" [moquette-im] [OnlineCallback] 用户" + clientId + "上线了!");
		}
		
		return result;
	}
}
