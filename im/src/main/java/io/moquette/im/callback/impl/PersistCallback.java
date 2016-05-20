package io.moquette.im.callback.impl;

import io.moquette.im.processor.PersistProcessor;
import io.moquette.proto.messages.PublishMessage;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * ClassName: PersistCallback 
 * @Description: 持久化IM消息对象
 * @author cuidonghuan
 * @date 2016年5月20日 下午12:49:09
 */

public class PersistCallback implements MethodInterceptor {

	@Override
	public Object intercept(Object obj, Method method, Object[] args,
			MethodProxy proxy) throws Throwable {

		Object result = null;
		
		result = proxy.invokeSuper(obj, args);
		
		// IM消息对象的byte[]形式
		byte[] payload = ((PublishMessage) args[1]).getPayload().array();
		// 回调持久化IM消息对象方法
		PersistProcessor.getInstance(null).onPersist(payload);
		
		return result;
	}

}
