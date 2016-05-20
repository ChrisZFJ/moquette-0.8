package io.moquette.im.callback.impl;

import io.moquette.im.processor.StateChangeProcessor;
import io.moquette.im.proxy.ProtocolProcessorProxy;
import io.moquette.server.ServerChannel;
import io.moquette.server.netty.NettyChannel;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * ClassName: OfflineCallback
 * @Description: 用户离线通知实现类,该类中用户客户端连接处于disconnect/lost时，回调相应的方法.
 * @author cuidonghuan
 * @date 2016年5月18日 下午1:18:00
 */

public class OfflineCallback implements MethodInterceptor {

	@Override
	public Object intercept(Object obj, Method method, Object[] args,
			MethodProxy proxy) throws Throwable {
		
		Object result = null;
		// 离线通知等逻辑操作...
		result = proxy.invokeSuper(obj, args);
		
		// 推送离线通知
		String clientId;
		// 主动离线
		if("processDisconnect".equals(method.getName())) {
			clientId = (String) (((ServerChannel)args[0]).getAttribute(NettyChannel.ATTR_KEY_CLIENTID));
		} else if("processConnectionLost".equals(method.getName())) {
			// 被动离线(比如客户端崩溃或者被挤下线)
			clientId = (String) args[0];
		} else {
			System.out.println(" [moquette-im] [OfflineCallback] 无法获得下线的clientId!");
			return result;
		}
		
		// 回调离线处理方法	
		StateChangeProcessor.getInstance(null).onOffline(clientId);
		
		// 判断是否为server_clientId触发，若是则不推送离线通知.
		if(!ProtocolProcessorProxy.getInstance().containsCertainStrings(clientId)) {
			// 调用notifyStatesChanged()方法
			ProtocolProcessorProxy.getInstance().notifyStatesChanged(clientId, false);
			System.out.println(" [moquette-im] [OnlineCallback] 用户" + clientId + "离线了! caused by " + "[" + method.getName() + "]");
		}
		
		return result;
	}

}
