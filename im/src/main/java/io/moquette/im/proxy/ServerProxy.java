package io.moquette.im.proxy;

import io.moquette.im.callback.filter.ServerProxyCallbackFilter;
import io.moquette.im.callback.impl.ServerStartCallback;
import io.moquette.server.Server;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.proxy.NoOp;

/**
 * ClassName: ServerProxy 
 * @Description: Server代理类，增强Server功能
 * @author cuidonghuan
 * @date 2016年5月16日 下午4:50:26
 */

public class ServerProxy implements MethodInterceptor {
	
	/** 默认动作 */
	public static final int DEFAULT = 0;
	
	/** IM Server启动时 */
	public static final int START_SERVER = 1;
	
	private static Server instance = null;
	
	/**
	 * @Description: 获得Server代理对象单例
	 * @return Server   
	 * @author cuidonghuan
	 * @date 2016年5月19日 下午2:32:57
	 */
	public static Server getInstance() {
		if (null == instance) {
			instance = getServerProxy();
		}
		return instance;
	}
	
	/**
	 * 获取代理对象
	 * @param clazz Server的委托类
	 */
	private static Server getServerProxy() {
		
		// 回调接口集合定义
		// 下标为0的Callback为NoOp.INSTANCE，NoOp实现类会使用默认的父类实现,没增加任何逻辑处理
		Callback[] callbacks = new Callback[]{NoOp.INSTANCE, new ServerStartCallback()};
		
		Enhancer enhancer = new Enhancer();
		// 设置需要创建子类的类 
		enhancer.setSuperclass(Server.class);
		enhancer.setCallbacks(callbacks);
		enhancer.setCallbackFilter(new ServerProxyCallbackFilter());
		
		// 通过字节码技术动态创建子类实例  
		return (Server) enhancer.create();
	}

	@Override
	public Object intercept(Object object, Method method, Object[] args,
			MethodProxy proxy) throws Throwable {
		proxy.invokeSuper(object, args);
		return null;
	}
	
	

}
