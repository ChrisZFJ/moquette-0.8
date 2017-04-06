package io.moquette.im.callback.impl;

import io.moquette.im.proxy.ProtocolProcessorProxy;
import io.moquette.spi.impl.SimpleMessaging;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * ClassName: ServerStartCallback 
 * @Description: MQTT IM Server启动回调接口
 * @author cuidonghuan
 * @date 2016年5月19日 上午8:36:19
 */

public class ServerStartCallback implements MethodInterceptor {

	@Override
	public Object intercept(Object obj, Method method, Object[] args,
			MethodProxy proxy) throws Throwable {
		
		// 这里执行增强处理
		Object result = null;
		
		// startSever(para1,para2,para3,para4,para5)
		if (null != args && args.length == 5) {
			// 在含有5个参数的Server.startServer()方法执行前，先初始化SimpleMessaging对象
			SimpleMessaging.getInstance().setM_processor(ProtocolProcessorProxy.getInstance());
		}
		
		result = proxy.invokeSuper(obj, args);
		return result;		
	}
}
