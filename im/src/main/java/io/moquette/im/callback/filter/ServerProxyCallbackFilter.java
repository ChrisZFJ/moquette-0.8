package io.moquette.im.callback.filter;

import io.moquette.im.proxy.ServerProxy;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.CallbackFilter;

/**
 * ClassName: ServerProxyCallbackFilter 
 * @Description: Server代理类的回调过滤器，负责调用多个相应的Callback实例
 * @author cuidonghuan
 * @date 2016年5月18日 下午6:36:44
 */

public class ServerProxyCallbackFilter implements CallbackFilter {

	@Override
	public int accept(Method method) {
		if ("startServer".equals(method.getName())) {
			return ServerProxy.START_SERVER;
		}
		
		return ServerProxy.DEFAULT;
	}

}
